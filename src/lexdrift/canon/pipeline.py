"""The canonicalization pipeline.

A rule reports edits; it never rewrites text itself. The pipeline applies them,
which is what lets it keep an offset map and reject two rules that disagree
about the same characters instead of silently letting the later one win.

Rules run in a fixed order and each one sees the output of the last, so an
earlier rule can change what a later one matches. That is deliberate: citation
normalization has to run before number normalization, or ``41-3-101`` is read as
three numbers.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from typing import Protocol, runtime_checkable

from lexdrift.config import CanonConfig
from lexdrift.errors import CanonError
from lexdrift.util.offsets import OffsetMap, OffsetMapBuilder
from lexdrift.util.spans import TextSpan

__all__ = [
    "CanonPipeline",
    "CanonResult",
    "Edit",
    "Rule",
    "apply_edits",
]


@dataclass(frozen=True, slots=True)
class Edit:
    """Replace ``span`` with ``replacement``."""

    span: TextSpan
    replacement: str

    @property
    def is_deletion(self) -> bool:
        return not self.replacement

    @property
    def delta(self) -> int:
        return len(self.replacement) - self.span.length


@runtime_checkable
class Rule(Protocol):
    """A canonicalization rule."""

    name: str

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        """Report the edits this rule wants applied to ``text``."""


@dataclass(frozen=True, slots=True)
class CanonResult:
    """The canonical text plus the trail back to the source."""

    source: str
    text: str
    mapping: OffsetMap
    applied: tuple[str, ...]

    def to_source(self, span: TextSpan) -> TextSpan:
        return self.mapping.to_source_span(span)

    def quote(self, span: TextSpan) -> str:
        """Quote a canonical span using the words of the source text."""

        return self.to_source(span).slice_of(self.source)


def apply_edits(text: str, edits: Sequence[Edit], *, rule: str = "") -> tuple[str, OffsetMap]:
    """Apply non-overlapping edits left to right, tracking offsets."""

    ordered = sorted(edits, key=lambda edit: (edit.span.start, edit.span.end))
    builder = OffsetMapBuilder()
    parts: list[str] = []
    cursor = 0
    for edit in ordered:
        if edit.span.start < cursor:
            raise CanonError(
                "canonicalization rule produced overlapping edits",
                rule=rule,
                at=edit.span.start,
            )
        if edit.span.end > len(text):
            raise CanonError(
                "canonicalization edit runs past the end of the text",
                rule=rule,
                end=edit.span.end,
                length=len(text),
            )
        gap = edit.span.start - cursor
        if gap:
            parts.append(text[cursor : edit.span.start])
            builder.keep(gap)
        parts.append(edit.replacement)
        builder.replace(edit.span.length, len(edit.replacement))
        cursor = edit.span.end
    if cursor < len(text):
        parts.append(text[cursor:])
        builder.keep(len(text) - cursor)
    return "".join(parts), builder.build(source_length=len(text))


class CanonPipeline:
    """An ordered set of rules applied as one rewrite."""

    def __init__(self, rules: Sequence[Rule]) -> None:
        names = [rule.name for rule in rules]
        duplicates = {name for name in names if names.count(name) > 1}
        if duplicates:
            raise CanonError(
                "pipeline has duplicate rule names", names=",".join(sorted(duplicates))
            )
        self._rules = tuple(rules)

    @property
    def rule_names(self) -> tuple[str, ...]:
        return tuple(rule.name for rule in self._rules)

    def run(self, text: str, config: CanonConfig | None = None) -> CanonResult:
        settings = config or CanonConfig()
        current = text
        mapping = OffsetMap.identity(len(text))
        applied: list[str] = []
        for rule in self._rules:
            edits = list(rule.edits(current, settings))
            if not edits:
                continue
            current, step = apply_edits(current, edits, rule=rule.name)
            mapping = mapping.compose(step)
            applied.append(rule.name)
        return CanonResult(source=text, text=current, mapping=mapping, applied=tuple(applied))

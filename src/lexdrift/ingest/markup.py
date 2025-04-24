"""Strike and insert markup used by amendment drafts.

Amendments are published as the section being amended with the removed words
wrapped in ``{-...-}`` and the added words in ``{+...+}``. That single document
describes two texts, and the engine needs both: the current law to match against
an existing corpus, and the proposed law to match against future bills.

Both are produced in one pass, each with an offset map back to the markup, so a
passage found in either can still be quoted with its markup intact.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import StrEnum

from lexdrift.errors import ParseError
from lexdrift.util.offsets import OffsetMap, OffsetMapBuilder
from lexdrift.util.spans import TextSpan

__all__ = [
    "Change",
    "ChangeKind",
    "MarkedText",
    "has_markup",
    "parse_markup",
]

_TAG_RE = re.compile(r"\{-(?P<struck>.*?)-\}|\{\+(?P<inserted>.*?)\+\}", re.DOTALL)
_OPENER_RE = re.compile(r"\{[-+]")
_TAG_OPEN = "{-"
_TAG_CLOSE = "-}"


class ChangeKind(StrEnum):
    STRIKE = "strike"
    INSERT = "insert"


@dataclass(frozen=True, slots=True)
class Change:
    """One marked edit, located in the markup."""

    kind: ChangeKind
    text: str
    span: TextSpan

    @property
    def is_strike(self) -> bool:
        return self.kind is ChangeKind.STRIKE


@dataclass(frozen=True, slots=True)
class MarkedText:
    """The two readings of one marked-up passage."""

    markup: str
    current: str
    proposed: str
    changes: tuple[Change, ...]
    current_map: OffsetMap
    proposed_map: OffsetMap

    @property
    def is_amendment(self) -> bool:
        return bool(self.changes)

    def struck(self) -> tuple[Change, ...]:
        return tuple(change for change in self.changes if change.is_strike)

    def inserted(self) -> tuple[Change, ...]:
        return tuple(change for change in self.changes if not change.is_strike)


def has_markup(text: str) -> bool:
    """True when the text carries at least one well-formed tag."""

    return _TAG_RE.search(text) is not None


def _line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def parse_markup(text: str) -> MarkedText:
    """Split marked-up text into its current and proposed readings."""

    current_parts: list[str] = []
    proposed_parts: list[str] = []
    changes: list[Change] = []
    current_map = OffsetMapBuilder()
    proposed_map = OffsetMapBuilder()
    cursor = 0
    for match in _TAG_RE.finditer(text):
        literal = text[cursor : match.start()]
        _guard_stray_openers(text, cursor, match.start())
        if literal:
            current_parts.append(literal)
            proposed_parts.append(literal)
            current_map.keep(len(literal))
            proposed_map.keep(len(literal))
        span = TextSpan(match.start(), match.end())
        struck = match.group("struck")
        if struck is not None:
            changes.append(Change(ChangeKind.STRIKE, struck, span))
            current_parts.append(struck)
            _keep_inside_tag(current_map, len(struck))
            proposed_map.drop(span.length)
        else:
            inserted = match.group("inserted") or ""
            changes.append(Change(ChangeKind.INSERT, inserted, span))
            proposed_parts.append(inserted)
            _keep_inside_tag(proposed_map, len(inserted))
            current_map.drop(span.length)
        cursor = match.end()
    _guard_stray_openers(text, cursor, len(text))
    tail = text[cursor:]
    if tail:
        current_parts.append(tail)
        proposed_parts.append(tail)
        current_map.keep(len(tail))
        proposed_map.keep(len(tail))
    return MarkedText(
        markup=text,
        current="".join(current_parts),
        proposed="".join(proposed_parts),
        changes=tuple(changes),
        current_map=current_map.build(source_length=len(text)),
        proposed_map=proposed_map.build(source_length=len(text)),
    )


def _keep_inside_tag(builder: OffsetMapBuilder, length: int) -> None:
    """Drop the two-character delimiters and keep the words between them aligned."""

    builder.drop(len(_TAG_OPEN))
    builder.keep(length)
    builder.drop(len(_TAG_CLOSE))


def _guard_stray_openers(text: str, start: int, end: int) -> None:
    stray = _OPENER_RE.search(text, start, end)
    if stray is not None:
        raise ParseError(
            "amendment markup has an unclosed tag",
            line=_line_of(text, stray.start()),
            column=stray.start() - text.rfind("\n", 0, stray.start()) - 1,
            tag=text[stray.start() : stray.start() + 2],
        )

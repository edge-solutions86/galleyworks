"""Comparing two prints of the same instrument.

Reuse detection answers "who else has this text". This answers the narrower and
more common question: "what changed between the introduced bill and the enrolled
one". It is a different problem and it wants a different tool, because the two
documents are known to correspond and the interesting output is the edit, not
the similarity.

Segments are paired by their printed path, so ``1(a)(ii)`` is compared with
``1(a)(ii)`` and a renumbered subsection shows up as one removal and one
addition rather than as a rewrite. That is the honest reading: a renumbering is
a change to the instrument even when no word moved.
"""

from __future__ import annotations

from dataclasses import dataclass
from difflib import SequenceMatcher
from enum import StrEnum

from lexdrift.canon.profiles import build_pipeline, profile
from lexdrift.canon.tokens import tokenize, words_only
from lexdrift.config import CanonConfig
from lexdrift.models import Instrument

__all__ = ["ChangeKind", "InstrumentDiff", "SegmentChange", "compare"]


class ChangeKind(StrEnum):
    UNCHANGED = "unchanged"
    CHANGED = "changed"
    ADDED = "added"
    REMOVED = "removed"


@dataclass(frozen=True, slots=True)
class SegmentChange:
    """What happened to one segment between two prints."""

    path: str
    kind: ChangeKind
    similarity: float = 1.0
    words_added: int = 0
    words_removed: int = 0

    @property
    def is_material(self) -> bool:
        return self.kind is not ChangeKind.UNCHANGED

    @property
    def churn(self) -> int:
        return self.words_added + self.words_removed


@dataclass(frozen=True, slots=True)
class InstrumentDiff:
    """The whole comparison."""

    left: str
    right: str
    changes: tuple[SegmentChange, ...]

    @property
    def is_identical(self) -> bool:
        return not any(change.is_material for change in self.changes)

    @property
    def changed(self) -> tuple[SegmentChange, ...]:
        return tuple(change for change in self.changes if change.is_material)

    def counts(self) -> dict[str, int]:
        found = {kind.value: 0 for kind in ChangeKind}
        for change in self.changes:
            found[change.kind.value] += 1
        return found

    @property
    def total_churn(self) -> int:
        return sum(change.churn for change in self.changes)


def _canonical_words(text: str, config: CanonConfig) -> list[str]:
    """Tokenize the canonical text, keeping case.

    Folding case here would override the profile: the verbatim profile exists so
    that a comparison can notice a capitalisation change, and lowering the
    tokens after canonicalization would silently take that away.
    """

    canon = build_pipeline().run(text, config)
    return [token.text for token in words_only(tokenize(canon.text))]


def _segments(instrument: Instrument, config: CanonConfig) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    for segment in instrument.body_segments():
        path = segment.path.printed
        if not path:
            continue
        found[path] = _canonical_words(segment.span.slice_of(instrument.raw_text), config)
    return found


def compare(
    left: Instrument, right: Instrument, config: CanonConfig | None = None
) -> InstrumentDiff:
    """Compare two prints of one instrument, segment by segment."""

    settings = config or profile("verbatim")
    before = _segments(left, settings)
    after = _segments(right, settings)
    changes: list[SegmentChange] = []
    for path in sorted(before.keys() | after.keys()):
        old = before.get(path)
        new = after.get(path)
        if old is None:
            changes.append(
                SegmentChange(
                    path=path,
                    kind=ChangeKind.ADDED,
                    similarity=0.0,
                    words_added=len(new or []),
                )
            )
            continue
        if new is None:
            changes.append(
                SegmentChange(
                    path=path,
                    kind=ChangeKind.REMOVED,
                    similarity=0.0,
                    words_removed=len(old),
                )
            )
            continue
        matcher = SequenceMatcher(a=old, b=new, autojunk=False)
        matched = sum(block.size for block in matcher.get_matching_blocks())
        changes.append(
            SegmentChange(
                path=path,
                kind=ChangeKind.UNCHANGED if old == new else ChangeKind.CHANGED,
                similarity=matcher.ratio(),
                words_added=len(new) - matched,
                words_removed=len(old) - matched,
            )
        )
    return InstrumentDiff(left=left.key.slug, right=right.key.slug, changes=tuple(changes))

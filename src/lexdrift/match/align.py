"""Turning shared fingerprints into passages.

A shared fingerprint is an anchor: the same run of tokens at some position in
each of two segments. Anchors on their own are not a finding, because a common
phrase produces one anywhere. What a report needs is a *passage*, a run of
anchors that advances through both documents together.

Chaining is greedy and monotone. Anchors are walked in query order and appended
to the open chain while the other document also moves forward and neither gap
exceeds ``max_gap``. A jump backwards ends the chain, because a passage that
reorders text is two passages, not one.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from lexdrift.errors import MatchError
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.util.spans import TextSpan

__all__ = ["Anchor", "Passage", "align", "anchors_between", "chain_anchors"]


@dataclass(frozen=True, slots=True, order=True)
class Anchor:
    """One fingerprint shared by two segments."""

    query_position: int
    other_position: int
    value: int
    query: TextSpan
    other: TextSpan


@dataclass(frozen=True, slots=True)
class Passage:
    """A run of anchors advancing through both documents."""

    query: TextSpan
    other: TextSpan
    anchors: tuple[Anchor, ...]

    @property
    def anchor_count(self) -> int:
        return len(self.anchors)

    @property
    def query_length(self) -> int:
        return self.query.length

    @property
    def other_length(self) -> int:
        return self.other.length

    @property
    def length(self) -> int:
        """The shorter of the two sides, which is what a passage really covers."""

        return min(self.query.length, self.other.length)


def anchors_between(query: SegmentFingerprint, other: SegmentFingerprint) -> list[Anchor]:
    """Every fingerprint the two segments share, in query order."""

    other_by_value = {shingle.value: shingle for shingle in other.selected}
    found: list[Anchor] = []
    for shingle in query.selected:
        partner = other_by_value.get(shingle.value)
        if partner is None:
            continue
        found.append(
            Anchor(
                query_position=shingle.position,
                other_position=partner.position,
                value=shingle.value,
                query=shingle.span,
                other=partner.span,
            )
        )
    found.sort()
    return found


def chain_anchors(anchors: Sequence[Anchor], *, max_gap: int) -> list[list[Anchor]]:
    """Group anchors into monotone chains."""

    if max_gap < 0:
        raise MatchError("max_gap must not be negative", max_gap=max_gap)
    chains: list[list[Anchor]] = []
    current: list[Anchor] = []
    for anchor in anchors:
        if not current:
            current = [anchor]
            continue
        previous = current[-1]
        forward = anchor.other_position > previous.other_position
        query_gap = anchor.query_position - previous.query_position
        other_gap = anchor.other_position - previous.other_position
        if forward and query_gap <= max_gap and other_gap <= max_gap:
            current.append(anchor)
        else:
            chains.append(current)
            current = [anchor]
    if current:
        chains.append(current)
    return chains


def _hull(spans: Iterable[TextSpan]) -> TextSpan:
    ordered = list(spans)
    return TextSpan(min(span.start for span in ordered), max(span.end for span in ordered))


def align(
    query: SegmentFingerprint,
    other: SegmentFingerprint,
    *,
    max_gap: int = 40,
    min_anchors: int = 2,
) -> list[Passage]:
    """Align two segments into the passages they share."""

    if min_anchors < 1:
        raise MatchError("min_anchors must be at least one", min_anchors=min_anchors)
    passages: list[Passage] = []
    for group in chain_anchors(anchors_between(query, other), max_gap=max_gap):
        if len(group) < min_anchors:
            continue
        passages.append(
            Passage(
                query=_hull(anchor.query for anchor in group),
                other=_hull(anchor.other for anchor in group),
                anchors=tuple(group),
            )
        )
    passages.sort(key=lambda passage: (-passage.length, passage.query.start))
    return passages

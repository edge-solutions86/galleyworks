"""Resolving passages and matches that describe the same finding twice.

A subsection is indexed and so is the section that contains it, so one copied
clause is reported once against the subsection and again against its parent.
Both are true and only one is useful.

Resolution is by coverage, not by score. A passage is dropped when a longer
passage already covers most of it, and a match is dropped when another match on
the same query segment covers the same region of the same instrument. Keeping
the longer of the two keeps the parent when the whole section was copied and the
subsection when only the subsection was.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence

from lexdrift.errors import MatchError
from lexdrift.match.align import Passage
from lexdrift.match.engine import SegmentMatch
from lexdrift.util.spans import TextSpan

__all__ = ["dedupe_matches", "dedupe_passages", "overlap_fraction"]


def overlap_fraction(left: TextSpan, right: TextSpan) -> float:
    """How much of the shorter span the two share, in ``[0, 1]``."""

    shorter = min(left.length, right.length)
    if shorter == 0:
        return 0.0
    shared = left.intersection(right)
    return 0.0 if shared is None else shared.length / shorter


def dedupe_passages(passages: Sequence[Passage], *, min_overlap: float = 0.6) -> list[Passage]:
    """Keep the longest passage from each overlapping group."""

    if not 0.0 < min_overlap <= 1.0:
        raise MatchError("min_overlap must lie in (0, 1]", min_overlap=min_overlap)
    kept: list[Passage] = []
    for passage in sorted(passages, key=lambda item: (-item.length, item.query.start)):
        covered = any(
            overlap_fraction(passage.query, other.query) >= min_overlap
            and overlap_fraction(passage.other, other.other) >= min_overlap
            for other in kept
        )
        if not covered:
            kept.append(passage)
    kept.sort(key=lambda item: item.query.start)
    return kept


def dedupe_matches(
    matches: Iterable[SegmentMatch], *, min_overlap: float = 0.6
) -> list[SegmentMatch]:
    """Drop matches whose region is already reported by a longer match."""

    ordered = sorted(
        matches,
        key=lambda match: (-match.longest_passage, match.query.slug, match.other.slug),
    )
    kept: list[SegmentMatch] = []
    for match in ordered:
        if not any(_same_finding(match, other, min_overlap) for other in kept):
            kept.append(match)
    kept.sort(key=lambda match: (match.query.slug, match.other.slug))
    return kept


def _same_finding(left: SegmentMatch, right: SegmentMatch, min_overlap: float) -> bool:
    if left.query.key != right.query.key or left.other.key != right.other.key:
        return False
    for one in left.passages:
        for two in right.passages:
            if (
                overlap_fraction(one.query, two.query) >= min_overlap
                and overlap_fraction(one.other, two.other) >= min_overlap
            ):
                return True
    return False

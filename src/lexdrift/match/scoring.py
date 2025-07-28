"""Similarity scores over fingerprint sets.

Jaccard is symmetric and containment is not, and the difference matters here.
A model bill copied whole into a one-section local ordinance has low Jaccard,
because the ordinance is a small part of the act, and containment near one,
because nearly all of the ordinance came from the act. Reuse detection wants
containment; deduplication wants Jaccard.

Both are computed on the winnowed sets, so both are estimates. The alignment
stage is what turns an estimate into a passage a person can read.
"""

from __future__ import annotations

from collections.abc import Collection
from dataclasses import dataclass

from lexdrift.errors import MatchError

__all__ = [
    "PairScore",
    "containment",
    "jaccard",
    "overlap_coefficient",
    "score_pair",
]


def _as_sets(left: Collection[int], right: Collection[int]) -> tuple[set[int], set[int]]:
    return set(left), set(right)


def jaccard(left: Collection[int], right: Collection[int]) -> float:
    """Symmetric similarity: shared over combined."""

    a, b = _as_sets(left, right)
    if not a and not b:
        return 0.0
    return len(a & b) / len(a | b)


def containment(left: Collection[int], right: Collection[int]) -> float:
    """How much of ``left`` appears in ``right``."""

    a, b = _as_sets(left, right)
    if not a:
        return 0.0
    return len(a & b) / len(a)


def overlap_coefficient(left: Collection[int], right: Collection[int]) -> float:
    """Shared over the smaller of the two sets."""

    a, b = _as_sets(left, right)
    if not a or not b:
        return 0.0
    return len(a & b) / min(len(a), len(b))


@dataclass(frozen=True, slots=True)
class PairScore:
    """Every score for one pair of segments, computed once."""

    shared: int
    query_size: int
    other_size: int
    jaccard: float
    query_containment: float
    other_containment: float

    @property
    def is_empty(self) -> bool:
        return self.shared == 0

    @property
    def best_containment(self) -> float:
        return max(self.query_containment, self.other_containment)

    @property
    def direction(self) -> str:
        """Which way the copying most likely ran, on set sizes alone."""

        if self.query_containment > self.other_containment:
            return "query-into-other"
        if self.other_containment > self.query_containment:
            return "other-into-query"
        return "even"


def score_pair(query: Collection[int], other: Collection[int]) -> PairScore:
    """Score one pair of fingerprint sets."""

    a, b = _as_sets(query, other)
    if not a or not b:
        raise MatchError("cannot score an empty fingerprint set", query=len(a), other=len(b))
    shared = len(a & b)
    return PairScore(
        shared=shared,
        query_size=len(a),
        other_size=len(b),
        jaccard=shared / len(a | b),
        query_containment=shared / len(a),
        other_containment=shared / len(b),
    )

"""Winnowing: keeping a fraction of the shingles without losing long matches.

Storing every shingle of a corpus is affordable exactly once and then never
again. Winnowing keeps the minimum hash of every window of ``w`` consecutive
shingles, which selects about ``2/(w+1)`` of them while guaranteeing that any
shared run of at least ``w + k - 1`` tokens is still detected.

Two details decide whether that guarantee actually holds, and both are easy to
get wrong:

* on a tie the **rightmost** minimum is taken, so that overlapping windows agree
  on the same occurrence and the same fingerprint is chosen in both documents;
* a hash already selected by the previous window is not recorded twice.
"""

from __future__ import annotations

from collections.abc import Sequence

from lexdrift.errors import MatchError
from lexdrift.fingerprint.shingle import Shingle

__all__ = ["expected_density", "guarantee_length", "winnow"]


def winnow(shingles: Sequence[Shingle], window: int) -> list[Shingle]:
    """Select the winnowed subset of ``shingles``."""

    if window < 1:
        raise MatchError("winnow window must be at least one", window=window)
    if not shingles:
        return []
    if window == 1:
        return list(shingles)
    selected: list[Shingle] = []
    last_position = -1
    for end in range(window - 1, len(shingles)):
        start = end - window + 1
        best = start
        for index in range(start, end + 1):
            # ``<=`` takes the rightmost minimum, which is what makes two
            # documents agree on the same occurrence inside a shared run.
            if shingles[index].value <= shingles[best].value:
                best = index
        if best != last_position:
            selected.append(shingles[best])
            last_position = best
    if not selected and shingles:
        selected.append(min(shingles[:window], key=lambda shingle: shingle.value))
    return selected


def expected_density(window: int) -> float:
    """The fraction of shingles winnowing is expected to keep."""

    if window < 1:
        raise MatchError("winnow window must be at least one", window=window)
    return 2.0 / (window + 1)


def guarantee_length(shingle_width: int, window: int) -> int:
    """Shortest shared run this configuration is guaranteed to detect."""

    if shingle_width < 1 or window < 1:
        raise MatchError(
            "shingle width and winnow window must both be at least one",
            width=shingle_width,
            window=window,
        )
    return shingle_width + window - 1

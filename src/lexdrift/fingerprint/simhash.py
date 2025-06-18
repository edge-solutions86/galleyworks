"""SimHash, the cheap filter in front of the expensive comparison.

A SimHash is one 64-bit word per segment, and two segments that differ by a few
words differ by a few bits. It is far coarser than MinHash but costs one integer
to store and one XOR plus a popcount to compare, so it is what the index uses to
throw away obviously unrelated pairs before any set arithmetic runs.

Weights matter. Unweighted, a segment's SimHash is dominated by the boilerplate
every act repeats, and every act in a corpus lands within a few bits of every
other. The caller supplies the weights; :func:`weighted_features` builds the
usual inverse-frequency ones.
"""

from __future__ import annotations

import math
from collections.abc import Iterable, Mapping, Sequence

from lexdrift.errors import MatchError
from lexdrift.fingerprint.hashing import hash_token
from lexdrift.util.bitops import BITS64, hamming, similarity_from_hamming

__all__ = [
    "simhash",
    "simhash_distance",
    "simhash_of_tokens",
    "weighted_features",
]


def simhash(features: Iterable[tuple[int, float]]) -> int:
    """Fold weighted 64-bit feature hashes into one 64-bit signature."""

    accumulator = [0.0] * BITS64
    seen = False
    for value, weight in features:
        seen = True
        if weight < 0:
            raise MatchError("simhash weights must not be negative", weight=weight)
        for bit in range(BITS64):
            if (value >> (BITS64 - 1 - bit)) & 1:
                accumulator[bit] += weight
            else:
                accumulator[bit] -= weight
    if not seen:
        return 0
    signature = 0
    for bit, total in enumerate(accumulator):
        if total > 0:
            signature |= 1 << (BITS64 - 1 - bit)
    return signature


def weighted_features(
    tokens: Sequence[str],
    document_frequency: Mapping[str, int] | None = None,
    *,
    corpus_size: int = 0,
) -> list[tuple[int, float]]:
    """Hash tokens and weight them by inverse document frequency.

    With no frequency table every token weighs one, which is the right default
    for a single document seen on its own.
    """

    if document_frequency is None or corpus_size <= 0:
        return [(hash_token(token), 1.0) for token in tokens]
    features: list[tuple[int, float]] = []
    for token in tokens:
        seen_in = document_frequency.get(token, 0)
        weight = math.log((corpus_size + 1) / (seen_in + 1)) + 1.0
        features.append((hash_token(token), weight))
    return features


def simhash_of_tokens(tokens: Sequence[str]) -> int:
    """SimHash of an unweighted token sequence."""

    return simhash(weighted_features(tokens))


def simhash_distance(left: int, right: int) -> int:
    """Bit distance between two signatures."""

    return hamming(left, right)


def simhash_similarity(left: int, right: int) -> float:
    """Similarity implied by the bit distance, in ``[0, 1]``."""

    return similarity_from_hamming(simhash_distance(left, right))

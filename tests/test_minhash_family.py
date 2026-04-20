"""The statistical properties the affine family has to keep.

Swapping the permutation construction is the kind of change that passes every
example test and quietly ruins the estimator, because a signature still has the
right shape when the family is correlated. These tests measure the estimate
rather than the mechanism.
"""

from __future__ import annotations

import pytest

from lexdrift.config import LshConfig
from lexdrift.fingerprint.minhash import MinHashSigner, jaccard_estimate


def signer(permutations: int = 256) -> MinHashSigner:
    return MinHashSigner(LshConfig(permutations=permutations, bands=permutations // 4))


def universe(start: int, count: int) -> list[int]:
    """A block of distinct 64-bit values with no structure of its own."""

    return [0x9E3779B97F4A7C15 * (start + n) & ((1 << 64) - 1) for n in range(count)]


def true_jaccard(left: list[int], right: list[int]) -> float:
    a, b = set(left), set(right)
    return len(a & b) / len(a | b)


@pytest.mark.parametrize("overlap", [0, 25, 50, 75, 100])
def test_the_estimate_tracks_the_truth(overlap: int) -> None:
    left = universe(0, 100)
    right = universe(100 - overlap, 100)
    estimate = jaccard_estimate(signer().sign(left), signer().sign(right))
    assert abs(estimate - true_jaccard(left, right)) < 0.12


def test_identical_sets_estimate_exactly_one() -> None:
    left = universe(0, 200)
    assert jaccard_estimate(signer().sign(left), signer().sign(left)) == 1.0


def test_disjoint_sets_estimate_near_zero() -> None:
    estimate = jaccard_estimate(signer().sign(universe(0, 200)), signer().sign(universe(500, 200)))
    assert estimate < 0.05


def test_the_estimate_is_monotone_in_the_overlap() -> None:
    left = universe(0, 100)
    estimates = [
        jaccard_estimate(signer().sign(left), signer().sign(universe(100 - overlap, 100)))
        for overlap in (10, 40, 70, 100)
    ]
    assert estimates == sorted(estimates)


def test_a_wider_signature_is_not_worse() -> None:
    left = universe(0, 100)
    right = universe(50, 100)
    truth = true_jaccard(left, right)
    narrow = abs(jaccard_estimate(signer(64).sign(left), signer(64).sign(right)) - truth)
    wide = abs(jaccard_estimate(signer(512).sign(left), signer(512).sign(right)) - truth)
    assert wide <= narrow + 0.1


def test_two_seeds_estimate_the_same_truth() -> None:
    left = universe(0, 100)
    right = universe(60, 100)
    first = MinHashSigner(LshConfig(seed=1))
    second = MinHashSigner(LshConfig(seed=99))
    truth = true_jaccard(left, right)
    for family in (first, second):
        assert abs(jaccard_estimate(family.sign(left), family.sign(right)) - truth) < 0.15


def test_signatures_from_different_seeds_are_not_comparable() -> None:
    left = universe(0, 100)
    first = MinHashSigner(LshConfig(seed=1)).sign(left)
    second = MinHashSigner(LshConfig(seed=99)).sign(left)
    assert jaccard_estimate(first, second) < 0.1


def test_order_and_duplication_do_not_matter() -> None:
    values = universe(0, 100)
    shuffled = [*values[50:], *values[:50], *values[:10]]
    assert signer(64).sign(values) == signer(64).sign(shuffled)


def test_a_large_set_still_signs_quickly_enough_to_test() -> None:
    signature = signer(64).sign(universe(0, 5000))
    assert len(signature) == 64
    assert len(set(signature)) > 32

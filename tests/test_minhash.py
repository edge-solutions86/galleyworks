from __future__ import annotations

import pytest
from hypothesis import given
from hypothesis import strategies as st

from lexdrift.config import LshConfig
from lexdrift.errors import MatchError
from lexdrift.fingerprint.hashing import MASK64, hash_token
from lexdrift.fingerprint.minhash import (
    MinHashSigner,
    jaccard_estimate,
    signature_from_bytes,
    signature_to_bytes,
)


def values(words: str) -> list[int]:
    return [hash_token(word) for word in words.split()]


def signer(permutations: int = 64) -> MinHashSigner:
    return MinHashSigner(LshConfig(permutations=permutations, bands=permutations // 4))


def test_signature_width_follows_the_config() -> None:
    assert signer(32).width == 32
    assert len(signer(32).sign(values("a b c"))) == 32


def test_signing_is_order_independent() -> None:
    forward = signer().sign(values("the board shall act"))
    backward = signer().sign(values("act shall board the"))
    assert forward == backward


def test_signing_ignores_duplicates() -> None:
    once = signer().sign(values("the board shall"))
    twice = signer().sign(values("the board shall the board"))
    assert once == twice


def test_signing_is_deterministic_across_instances() -> None:
    assert signer().sign(values("a b c")) == signer().sign(values("a b c"))


def test_a_different_seed_gives_a_different_signature() -> None:
    left = MinHashSigner(LshConfig(seed=1)).sign(values("a b c"))
    right = MinHashSigner(LshConfig(seed=2)).sign(values("a b c"))
    assert left != right


def test_an_empty_set_signs_as_empty() -> None:
    signature = signer().sign([])
    assert set(signature) == {MASK64}
    assert signer().is_empty(signature)


def test_identical_sets_estimate_one() -> None:
    signature = signer(128).sign(values("the board shall publish a schedule"))
    assert jaccard_estimate(signature, signature) == 1.0


def test_disjoint_sets_estimate_near_zero() -> None:
    left = signer(128).sign(values("alpha bravo charlie delta echo"))
    right = signer(128).sign(values("foxtrot golf hotel india juliett"))
    assert jaccard_estimate(left, right) < 0.2


def test_a_half_shared_set_estimates_near_a_third() -> None:
    left = signer(256).sign(values("a b c d e f g h"))
    right = signer(256).sign(values("e f g h i j k l"))
    assert 0.15 < jaccard_estimate(left, right) < 0.5


def test_the_estimate_rises_with_real_overlap() -> None:
    base = values("a b c d e f g h i j")
    near = values("a b c d e f g h i x")
    far = values("a b c d e p q r s t")
    signer_ = signer(256)
    close = jaccard_estimate(signer_.sign(base), signer_.sign(near))
    distant = jaccard_estimate(signer_.sign(base), signer_.sign(far))
    assert close > distant


def test_comparing_different_widths_is_rejected() -> None:
    with pytest.raises(MatchError, match="different widths"):
        jaccard_estimate((1, 2, 3), (1, 2))


def test_comparing_empty_signatures_is_rejected() -> None:
    with pytest.raises(MatchError, match="empty signatures"):
        jaccard_estimate((), ())


@given(st.lists(st.integers(min_value=0, max_value=MASK64), min_size=1, max_size=16))
def test_signatures_round_trip_through_bytes(signature: list[int]) -> None:
    blob = signature_to_bytes(signature)
    assert len(blob) == 8 * len(signature)
    assert signature_from_bytes(blob) == tuple(signature)


def test_a_truncated_blob_is_rejected() -> None:
    with pytest.raises(MatchError, match="whole number of words"):
        signature_from_bytes(b"1234567")


def test_packing_is_big_endian() -> None:
    assert signature_to_bytes([1]) == b"\x00\x00\x00\x00\x00\x00\x00\x01"

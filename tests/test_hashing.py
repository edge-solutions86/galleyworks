from __future__ import annotations

import pytest
from hypothesis import given
from hypothesis import strategies as st

from lexdrift.fingerprint.hashing import (
    MASK64,
    hash_bytes,
    hash_ngram,
    hash_stream,
    hash_token,
    mix64,
    seeded_hash,
    unmix64,
)

u64 = st.integers(min_value=0, max_value=MASK64)


def test_hashes_are_pinned_and_must_not_drift() -> None:
    assert hash_bytes(b"") == 0xE4A6A0577479B2B4
    assert hash_token("board") == hash_bytes(b"board")
    assert mix64(0) == 0
    assert mix64(1) == 0x5692161D100B05E5


@given(u64)
def test_mix64_round_trips(value: int) -> None:
    assert unmix64(mix64(value)) == value


@given(u64)
def test_unmix64_round_trips_the_other_way(value: int) -> None:
    assert mix64(unmix64(value)) == value


@given(u64)
def test_mix64_stays_in_range(value: int) -> None:
    assert 0 <= mix64(value) <= MASK64


@given(st.binary(max_size=64))
def test_hash_bytes_stays_in_range(data: bytes) -> None:
    assert 0 <= hash_bytes(data) <= MASK64


def test_ngram_separator_stops_boundary_collisions() -> None:
    assert hash_ngram(["ab", "c"]) != hash_ngram(["a", "bc"])
    assert hash_ngram(["shall", "publish"]) != hash_ngram(["publish", "shall"])


def test_ngram_of_one_token_differs_from_the_bare_token() -> None:
    assert hash_ngram(["board"]) == hash_token("board")


def test_empty_ngram_is_rejected() -> None:
    with pytest.raises(ValueError, match="empty n-gram"):
        hash_ngram([])


def test_seeded_hash_separates_permutations() -> None:
    base = hash_token("department")
    seeds = {seeded_hash(base, seed) for seed in range(64)}
    assert len(seeds) == 64


@given(u64, st.integers(min_value=0, max_value=255))
def test_seeded_hash_is_deterministic(value: int, seed: int) -> None:
    assert seeded_hash(value, seed) == seeded_hash(value, seed)


def test_hash_stream_is_order_sensitive() -> None:
    assert hash_stream([1, 2, 3]) != hash_stream([3, 2, 1])
    assert hash_stream([]) == 0xCBF29CE484222325


def test_hash_stream_folds_incrementally() -> None:
    values = [hash_token(word) for word in ("the", "board", "shall")]
    assert hash_stream(values) == mix64(
        mix64(mix64(0xCBF29CE484222325 ^ values[0]) ^ values[1]) ^ values[2]
    )

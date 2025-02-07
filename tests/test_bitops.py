from __future__ import annotations

import pytest
from hypothesis import given
from hypothesis import strategies as st

from lexdrift.util.bitops import (
    BITS64,
    MASK64,
    band_of,
    bands,
    hamming,
    popcount,
    rotl64,
    rotr64,
    sign_vector,
    similarity_from_hamming,
)

u64 = st.integers(min_value=0, max_value=MASK64)


def test_popcount_masks_to_64_bits() -> None:
    assert popcount(0) == 0
    assert popcount(MASK64) == 64
    assert popcount(MASK64 | (1 << 70)) == 64


def test_hamming_is_symmetric_and_zero_on_equal() -> None:
    assert hamming(0b1011, 0b1011) == 0
    assert hamming(0b1011, 0b1000) == 2
    assert hamming(0, MASK64) == 64


@given(u64, u64)
def test_hamming_is_symmetric(left: int, right: int) -> None:
    assert hamming(left, right) == hamming(right, left)


@given(u64, st.integers(min_value=0, max_value=200))
def test_rotations_are_inverse(value: int, count: int) -> None:
    assert rotr64(rotl64(value, count), count) == value


@given(u64)
def test_rotation_preserves_popcount(value: int) -> None:
    assert popcount(rotl64(value, 17)) == popcount(value)


def test_rotation_by_zero_and_full_width_is_identity() -> None:
    assert rotl64(0xDEADBEEF, 0) == 0xDEADBEEF
    assert rotl64(0xDEADBEEF, 64) == 0xDEADBEEF


def test_bands_split_most_significant_first() -> None:
    value = (0xAB << 56) | (0xCD << 48)
    chunks = list(bands(value, count=8))
    assert len(chunks) == 8
    assert chunks[0] == 0xAB
    assert chunks[1] == 0xCD
    assert chunks[2] == 0


def test_bands_reassemble_the_original() -> None:
    value = 0x0123456789ABCDEF
    chunks = list(bands(value, count=4))
    rebuilt = 0
    for chunk in chunks:
        rebuilt = (rebuilt << 16) | chunk
    assert rebuilt == value


@pytest.mark.parametrize("count", [0, -1, 7, 24])
def test_bands_rejects_counts_that_do_not_divide(count: int) -> None:
    with pytest.raises(ValueError):
        list(bands(1, count=count))


def test_band_of_agrees_with_bands() -> None:
    value = 0x0123456789ABCDEF
    chunks = list(bands(value, count=8))
    assert [band_of(value, i, count=8) for i in range(8)] == chunks


def test_band_of_rejects_out_of_range_index() -> None:
    with pytest.raises(ValueError, match="out of range"):
        band_of(1, 8, count=8)


def test_sign_vector_matches_bit_order() -> None:
    vector = sign_vector(1 << 63)
    assert vector[0] == 1
    assert set(vector[1:]) == {-1}
    assert len(vector) == BITS64


def test_similarity_from_hamming_endpoints() -> None:
    assert similarity_from_hamming(0) == 1.0
    assert similarity_from_hamming(64) == 0.0
    assert similarity_from_hamming(32) == pytest.approx(0.5)


@pytest.mark.parametrize("distance", [-1, 65])
def test_similarity_rejects_impossible_distances(distance: int) -> None:
    with pytest.raises(ValueError, match="outside"):
        similarity_from_hamming(distance)

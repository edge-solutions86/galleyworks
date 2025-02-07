"""Bit twiddling used by simhash and by LSH band extraction.

Python has ``int.bit_count`` since 3.10, so nothing here reimplements popcount;
what it does provide is a consistent 64-bit view, because Python integers are
arbitrary precision and a shift left will happily produce a 65th bit.
"""

from __future__ import annotations

from collections.abc import Iterator

__all__ = [
    "BITS64",
    "MASK64",
    "band_of",
    "bands",
    "hamming",
    "popcount",
    "rotl64",
    "rotr64",
    "sign_vector",
    "similarity_from_hamming",
]

BITS64 = 64
MASK64 = (1 << BITS64) - 1


def popcount(value: int) -> int:
    """Number of set bits in the low 64 bits of ``value``."""

    return (value & MASK64).bit_count()


def hamming(left: int, right: int) -> int:
    """Bit distance between two 64-bit values."""

    return ((left ^ right) & MASK64).bit_count()


def rotl64(value: int, count: int) -> int:
    shift = count % BITS64
    v = value & MASK64
    if shift == 0:
        return v
    return ((v << shift) | (v >> (BITS64 - shift))) & MASK64


def rotr64(value: int, count: int) -> int:
    return rotl64(value, BITS64 - (count % BITS64))


def bands(value: int, *, count: int) -> Iterator[int]:
    """Split the low 64 bits into ``count`` equal bands, most significant first."""

    if count <= 0:
        raise ValueError(f"band count must be positive, got {count}")
    if BITS64 % count:
        raise ValueError(f"64 bits do not divide into {count} bands")
    width = BITS64 // count
    mask = (1 << width) - 1
    for index in range(count):
        shift = BITS64 - width * (index + 1)
        yield (value >> shift) & mask


def band_of(value: int, index: int, *, count: int) -> int:
    """Extract a single band without materialising the rest."""

    if index < 0 or index >= count:
        raise ValueError(f"band index {index} out of range for {count} bands")
    for position, chunk in enumerate(bands(value, count=count)):
        if position == index:
            return chunk
    raise AssertionError("unreachable: band index validated above")


def sign_vector(value: int) -> tuple[int, ...]:
    """Expand a 64-bit value to a tuple of +1 / -1, most significant bit first."""

    return tuple(1 if (value >> (BITS64 - 1 - i)) & 1 else -1 for i in range(BITS64))


def similarity_from_hamming(distance: int) -> float:
    """Cosine-like similarity estimate implied by a simhash distance."""

    if distance < 0 or distance > BITS64:
        raise ValueError(f"hamming distance {distance} outside [0, {BITS64}]")
    return 1.0 - distance / BITS64

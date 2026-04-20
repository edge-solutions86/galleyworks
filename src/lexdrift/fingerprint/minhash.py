"""MinHash signatures over shingle sets.

A signature is ``permutations`` independent minima, one per hash family. The
fraction of positions where two signatures agree estimates the Jaccard
similarity of the underlying sets, which is what lets the index compare two
documents without holding either shingle set.

The permutation family is affine over the Mersenne prime 2**61 - 1:
``h_i(x) = (a_i * x + b_i) mod p``. The earlier version derived each position
with a full 64-bit mix per (value, position) pair, which is two multiplies and
three shifts where this is one multiply and an add. On a corpus pass that inner
loop runs once per shingle per permutation, so it is the hot loop in the whole
engine.

The family is derived deterministically from the configured seed, so two indexes
built with the same :class:`~lexdrift.config.LshConfig` produce comparable
signatures on different machines and in different processes.
"""

from __future__ import annotations

import struct
from collections.abc import Iterable, Sequence

from lexdrift.config import LshConfig
from lexdrift.errors import MatchError
from lexdrift.fingerprint.hashing import MASK64, mix64

__all__ = [
    "MERSENNE_PRIME",
    "MinHashSigner",
    "jaccard_estimate",
    "signature_from_bytes",
    "signature_to_bytes",
]

MERSENNE_PRIME = (1 << 61) - 1
_EMPTY = MASK64


class MinHashSigner:
    """Builds fixed width signatures from shingle hashes."""

    __slots__ = ("_config", "_multipliers", "_offsets")

    def __init__(self, config: LshConfig | None = None) -> None:
        self._config = config or LshConfig()
        multipliers: list[int] = []
        offsets: list[int] = []
        state = self._config.seed
        for _ in range(self._config.permutations):
            state = mix64(state + 0x9E3779B97F4A7C15)
            # A multiplier of zero would collapse the permutation to a constant.
            multipliers.append(1 + (state % (MERSENNE_PRIME - 1)))
            state = mix64(state + 0x9E3779B97F4A7C15)
            offsets.append(state % MERSENNE_PRIME)
        self._multipliers = tuple(multipliers)
        self._offsets = tuple(offsets)

    @property
    def width(self) -> int:
        return self._config.permutations

    @property
    def config(self) -> LshConfig:
        return self._config

    def sign(self, values: Iterable[int]) -> tuple[int, ...]:
        """Return the signature of the set ``values``."""

        signature = [MERSENNE_PRIME] * self.width
        multipliers = self._multipliers
        offsets = self._offsets
        seen = False
        for value in values:
            seen = True
            base = value % MERSENNE_PRIME
            for index in range(self.width):
                candidate = (multipliers[index] * base + offsets[index]) % MERSENNE_PRIME
                if candidate < signature[index]:
                    signature[index] = candidate
        if not seen:
            return tuple([_EMPTY] * self.width)
        return tuple(signature)

    def is_empty(self, signature: Sequence[int]) -> bool:
        return all(value == _EMPTY for value in signature)


def jaccard_estimate(left: Sequence[int], right: Sequence[int]) -> float:
    """Estimate Jaccard similarity from two signatures of equal width."""

    if len(left) != len(right):
        raise MatchError("signatures have different widths", left=len(left), right=len(right))
    if not left:
        raise MatchError("cannot compare empty signatures")
    agree = sum(1 for a, b in zip(left, right, strict=True) if a == b)
    return agree / len(left)


def signature_to_bytes(signature: Sequence[int]) -> bytes:
    """Pack a signature for storage, big endian so it sorts bytewise."""

    return struct.pack(f">{len(signature)}Q", *signature)


def signature_from_bytes(blob: bytes) -> tuple[int, ...]:
    """Unpack a signature written by :func:`signature_to_bytes`."""

    if len(blob) % 8:
        raise MatchError("signature blob is not a whole number of words", size=len(blob))
    count = len(blob) // 8
    return struct.unpack(f">{count}Q", blob)

"""Deterministic 64-bit hashing.

Fingerprints are written to disk and compared across processes and machines, so
none of this may depend on ``PYTHONHASHSEED``. Everything here is either a
blake2b digest or an explicit integer mix, and both are stable forever.

``MASK64`` is applied after every arithmetic step because Python integers do not
wrap on their own.
"""

from __future__ import annotations

import hashlib
from collections.abc import Iterable, Sequence

__all__ = [
    "MASK64",
    "hash_bytes",
    "hash_ngram",
    "hash_stream",
    "hash_token",
    "mix64",
    "seeded_hash",
    "unmix64",
]

MASK64 = (1 << 64) - 1

_GOLDEN_GAMMA = 0x9E3779B97F4A7C15
_MIX_A = 0xBF58476D1CE4E5B9
_MIX_B = 0x94D049BB133111EB
_UNMIX_A = 0x319642B2D24D8EC3  # modular inverse of _MIX_B mod 2**64
_UNMIX_B = 0x96DE1B173F119089  # modular inverse of _MIX_A mod 2**64
_NGRAM_SEPARATOR = b"\x1f"


def mix64(value: int) -> int:
    """The splitmix64 finalizer: a bijection on 64 bits with good avalanche."""

    x = value & MASK64
    x = ((x ^ (x >> 30)) * _MIX_A) & MASK64
    x = ((x ^ (x >> 27)) * _MIX_B) & MASK64
    return (x ^ (x >> 31)) & MASK64


def unmix64(value: int) -> int:
    """Inverse of :func:`mix64`, used by tests and by index integrity checks."""

    x = value & MASK64
    x = (x ^ (x >> 31) ^ (x >> 62)) & MASK64
    x = (x * _UNMIX_A) & MASK64
    x = (x ^ (x >> 27) ^ (x >> 54)) & MASK64
    x = (x * _UNMIX_B) & MASK64
    return (x ^ (x >> 30) ^ (x >> 60)) & MASK64


def hash_bytes(data: bytes) -> int:
    """A stable 64-bit digest of ``data``."""

    return int.from_bytes(hashlib.blake2b(data, digest_size=8).digest(), "big")


def hash_token(token: str) -> int:
    """A stable 64-bit digest of one canonical token."""

    return hash_bytes(token.encode("utf-8"))


def hash_ngram(tokens: Sequence[str]) -> int:
    """Hash an ordered token n-gram.

    The separator matters: without it ``["ab", "c"]`` and ``["a", "bc"]`` would
    collide, and shingles of different widths would share fingerprints.
    """

    if not tokens:
        raise ValueError("cannot hash an empty n-gram")
    joined = _NGRAM_SEPARATOR.join(token.encode("utf-8") for token in tokens)
    return hash_bytes(joined)


def seeded_hash(value: int, seed: int) -> int:
    """Derive an independent hash of ``value`` for permutation family ``seed``."""

    return mix64((value & MASK64) ^ ((seed * _GOLDEN_GAMMA) & MASK64))


def hash_stream(values: Iterable[int]) -> int:
    """Fold a stream of 64-bit values into one digest, order sensitive."""

    acc = 0xCBF29CE484222325
    for value in values:
        acc = mix64(acc ^ (value & MASK64))
    return acc

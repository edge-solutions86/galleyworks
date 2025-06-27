"""Banded locality sensitive hashing over MinHash signatures.

A signature of ``b * r`` positions is cut into ``b`` bands of ``r`` rows. Two
segments become candidates when any band matches exactly. The probability of
that is ``1 - (1 - s**r)**b`` for true similarity ``s``, which is the S-curve
whose knee sits near ``(1/b)**(1/r)``.

Choosing ``b`` and ``r`` is the whole tuning story: more bands catches more
pairs and costs more comparisons, more rows per band does the reverse.
:func:`solve_banding` picks the split whose knee lands closest to a wanted
threshold, so an operator can ask for a similarity rather than for a banding.
"""

from __future__ import annotations

import hashlib
from collections.abc import Sequence

from lexdrift.config import LshConfig
from lexdrift.errors import ConfigError, MatchError

__all__ = [
    "band_key",
    "band_keys",
    "candidate_probability",
    "solve_banding",
]


def band_keys(signature: Sequence[int], config: LshConfig) -> list[bytes]:
    """Return one bucket key per band of ``signature``."""

    if len(signature) != config.permutations:
        raise MatchError(
            "signature width does not match the banding",
            width=len(signature),
            expected=config.permutations,
        )
    rows = config.rows
    return [
        band_key(index, signature[index * rows : (index + 1) * rows])
        for index in range(config.bands)
    ]


def band_key(index: int, rows: Sequence[int]) -> bytes:
    """Hash one band into a compact bucket key.

    The band index is folded in so that identical rows in different bands do not
    share a bucket, which would inflate the candidate set for no benefit.
    """

    digest = hashlib.blake2b(digest_size=16)
    digest.update(index.to_bytes(4, "big"))
    for value in rows:
        digest.update(value.to_bytes(8, "big"))
    return digest.digest()


def candidate_probability(similarity: float, *, bands: int, rows: int) -> float:
    """Probability that a pair at ``similarity`` shares at least one band."""

    if not 0.0 <= similarity <= 1.0:
        raise ConfigError("similarity must lie in [0, 1]", similarity=similarity)
    if bands < 1 or rows < 1:
        raise ConfigError("bands and rows must both be positive", bands=bands, rows=rows)
    return 1.0 - (1.0 - similarity**rows) ** bands


def solve_banding(threshold: float, permutations: int = 128, *, seed: int = 0x5EED) -> LshConfig:
    """Pick the banding whose knee sits closest to ``threshold``."""

    if not 0.0 < threshold < 1.0:
        raise ConfigError("threshold must lie strictly inside (0, 1)", threshold=threshold)
    best: LshConfig | None = None
    best_error = float("inf")
    for bands in range(1, permutations + 1):
        if permutations % bands:
            continue
        rows = permutations // bands
        knee = (1.0 / bands) ** (1.0 / rows)
        error = abs(knee - threshold)
        if error < best_error:
            best_error = error
            best = LshConfig(permutations=permutations, bands=bands, seed=seed)
    if best is None:  # pragma: no cover - permutations >= 8 always divides by one
        raise ConfigError("no banding divides the signature width", permutations=permutations)
    return best

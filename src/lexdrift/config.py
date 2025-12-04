"""Engine configuration.

Every knob that changes what a fingerprint means lives here, because two indexes
built with different settings cannot be compared. :meth:`EngineConfig.digest`
gives a short stable string that the index store records, so a mismatch is
caught on open rather than at query time when the answers are merely wrong.
"""

from __future__ import annotations

import json
from enum import StrEnum
from typing import Any, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator

from lexdrift.errors import ConfigError
from lexdrift.fingerprint.hashing import hash_bytes

__all__ = [
    "CanonConfig",
    "EngineConfig",
    "LshConfig",
    "MatchConfig",
    "ShingleConfig",
    "ShingleMode",
]


class ShingleMode(StrEnum):
    WORD = "word"
    CHARACTER = "character"


class _Frozen(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid", validate_default=True)


class ShingleConfig(_Frozen):
    """How the canonical token stream is cut into overlapping windows."""

    mode: ShingleMode = ShingleMode.WORD
    width: int = Field(default=5, ge=1, le=64)
    winnow_window: int = Field(default=4, ge=1, le=64)

    @property
    def guarantee_length(self) -> int:
        """Shortest passage the winnowing scheme is guaranteed to detect."""

        return self.width + self.winnow_window - 1


class LshConfig(_Frozen):
    """MinHash signature width and the banding used to bucket it."""

    permutations: int = Field(default=128, ge=8, le=1024)
    bands: int = Field(default=32, ge=1, le=512)
    seed: int = Field(default=0x5EED, ge=0)

    @model_validator(mode="after")
    def _bands_divide_permutations(self) -> Self:
        if self.permutations % self.bands:
            raise ValueError(f"bands={self.bands} does not divide permutations={self.permutations}")
        return self

    @property
    def rows(self) -> int:
        return self.permutations // self.bands

    @property
    def approximate_threshold(self) -> float:
        """The Jaccard similarity at which this banding is half likely to fire."""

        return float((1.0 / self.bands) ** (1.0 / self.rows))


class CanonConfig(_Frozen):
    """Which normalization rules run, and under which jurisdiction profile."""

    profile: str = "default"
    fold_case: bool = True
    fold_unicode: bool = True
    normalize_numbers: bool = True
    normalize_citations: bool = True
    drop_enumeration_labels: bool = True
    drop_enacting_clause: bool = True


class MatchConfig(_Frozen):
    """Thresholds applied after candidate generation."""

    min_containment: float = Field(default=0.35, ge=0.0, le=1.0)
    min_passage_chars: int = Field(default=90, ge=1)
    min_content_ratio: float = Field(default=0.25, ge=0.0, le=1.0)
    max_anchor_gap: int = Field(default=40, ge=0)
    max_candidates: int = Field(default=200, ge=1)
    order: Literal["score", "length"] = "score"


class EngineConfig(_Frozen):
    """The whole configuration surface."""

    shingle: ShingleConfig = ShingleConfig()
    lsh: LshConfig = LshConfig()
    canon: CanonConfig = CanonConfig()
    match: MatchConfig = MatchConfig()

    @classmethod
    def from_mapping(cls, data: dict[str, Any]) -> EngineConfig:
        try:
            return cls.model_validate(data)
        except Exception as exc:  # pydantic raises ValidationError
            raise ConfigError("engine configuration is invalid", detail=str(exc)) from exc

    def to_mapping(self) -> dict[str, Any]:
        return self.model_dump(mode="json")

    def digest(self) -> str:
        """A short stable identifier for the settings that shape an index."""

        shaping = {
            "shingle": self.shingle.model_dump(mode="json"),
            "lsh": self.lsh.model_dump(mode="json"),
            "canon": self.canon.model_dump(mode="json"),
        }
        blob = json.dumps(shaping, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return f"{hash_bytes(blob):016x}"

from __future__ import annotations

import pytest
from pydantic import ValidationError

from lexdrift.config import CanonConfig, EngineConfig, LshConfig, ShingleConfig, ShingleMode
from lexdrift.errors import ConfigError


def test_defaults_are_usable() -> None:
    cfg = EngineConfig()
    assert cfg.shingle.mode is ShingleMode.WORD
    assert cfg.lsh.rows == 4
    assert cfg.match.min_containment == pytest.approx(0.35)


def test_config_is_frozen() -> None:
    cfg = EngineConfig()
    with pytest.raises(ValidationError):
        cfg.shingle.width = 9  # type: ignore[misc]


def test_unknown_keys_are_rejected() -> None:
    with pytest.raises(ConfigError, match="invalid"):
        EngineConfig.from_mapping({"shingle": {"width": 4, "widht": 5}})


def test_bands_must_divide_permutations() -> None:
    with pytest.raises(ValueError, match="does not divide"):
        LshConfig(permutations=100, bands=32)


def test_rows_and_threshold_follow_the_banding() -> None:
    cfg = LshConfig(permutations=128, bands=16)
    assert cfg.rows == 8
    assert 0.0 < cfg.approximate_threshold < 1.0
    wider = LshConfig(permutations=128, bands=64)
    assert wider.approximate_threshold < cfg.approximate_threshold


def test_guarantee_length_follows_winnowing() -> None:
    cfg = ShingleConfig(width=5, winnow_window=4)
    assert cfg.guarantee_length == 8


def test_ranges_are_enforced() -> None:
    with pytest.raises(ValueError):
        ShingleConfig(width=0)
    with pytest.raises(ValueError):
        LshConfig(permutations=4)
    with pytest.raises(ConfigError):
        EngineConfig.from_mapping({"match": {"min_containment": 1.5}})


def test_round_trips_through_a_mapping() -> None:
    cfg = EngineConfig.from_mapping(
        {
            "shingle": {"mode": "character", "width": 9, "winnow_window": 6},
            "lsh": {"permutations": 64, "bands": 8},
            "canon": {"profile": "wy", "normalize_citations": False},
            "match": {"min_containment": 0.6, "order": "length"},
        }
    )
    assert cfg.shingle.mode is ShingleMode.CHARACTER
    assert cfg.canon.profile == "wy"
    assert EngineConfig.from_mapping(cfg.to_mapping()) == cfg


def test_digest_is_stable_and_shape_sensitive() -> None:
    base = EngineConfig()
    assert base.digest() == EngineConfig().digest()
    assert len(base.digest()) == 16
    changed = EngineConfig(shingle=ShingleConfig(width=6))
    assert changed.digest() != base.digest()


def test_digest_ignores_match_thresholds() -> None:
    base = EngineConfig()
    retuned = EngineConfig.from_mapping({**base.to_mapping(), "match": {"min_containment": 0.9}})
    assert retuned.digest() == base.digest()


def test_canon_profile_toggles_are_independent() -> None:
    canon = CanonConfig(normalize_numbers=False)
    assert not canon.normalize_numbers
    assert canon.normalize_citations

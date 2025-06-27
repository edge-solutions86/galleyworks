from __future__ import annotations

import pytest

from lexdrift.config import LshConfig
from lexdrift.errors import ConfigError, MatchError
from lexdrift.index.lsh import band_key, band_keys, candidate_probability, solve_banding


def signature(width: int = 32) -> list[int]:
    return list(range(width))


def test_one_key_per_band() -> None:
    config = LshConfig(permutations=32, bands=8)
    keys = band_keys(signature(), config)
    assert len(keys) == 8
    assert len(set(keys)) == 8


def test_keys_are_sixteen_bytes() -> None:
    keys = band_keys(signature(), LshConfig(permutations=32, bands=8))
    assert all(len(key) == 16 for key in keys)


def test_a_width_mismatch_is_rejected() -> None:
    with pytest.raises(MatchError, match="does not match the banding"):
        band_keys([1, 2, 3], LshConfig(permutations=32, bands=8))


def test_identical_signatures_share_every_key() -> None:
    config = LshConfig(permutations=32, bands=8)
    assert band_keys(signature(), config) == band_keys(signature(), config)


def test_one_changed_row_breaks_only_its_band() -> None:
    config = LshConfig(permutations=32, bands=8)
    changed = signature()
    changed[0] = 999
    left = band_keys(signature(), config)
    right = band_keys(changed, config)
    assert sum(1 for a, b in zip(left, right, strict=True) if a != b) == 1


def test_the_band_index_is_part_of_the_key() -> None:
    assert band_key(0, [1, 2]) != band_key(1, [1, 2])


def test_probability_endpoints() -> None:
    assert candidate_probability(0.0, bands=8, rows=4) == 0.0
    assert candidate_probability(1.0, bands=8, rows=4) == 1.0


def test_probability_rises_with_similarity() -> None:
    low = candidate_probability(0.3, bands=8, rows=4)
    high = candidate_probability(0.8, bands=8, rows=4)
    assert low < high


def test_more_bands_catch_more_pairs() -> None:
    few = candidate_probability(0.5, bands=4, rows=8)
    many = candidate_probability(0.5, bands=16, rows=2)
    assert many > few


def test_probability_rejects_bad_inputs() -> None:
    with pytest.raises(ConfigError, match=r"\[0, 1\]"):
        candidate_probability(1.5, bands=8, rows=4)
    with pytest.raises(ConfigError, match="must both be positive"):
        candidate_probability(0.5, bands=0, rows=4)


def test_solving_lands_near_the_asked_threshold() -> None:
    config = solve_banding(0.8, 128)
    assert config.permutations == 128
    assert abs(config.approximate_threshold - 0.8) < 0.1


def test_a_lower_threshold_asks_for_more_bands() -> None:
    loose = solve_banding(0.4, 128)
    strict = solve_banding(0.9, 128)
    assert loose.bands > strict.bands


def test_the_solved_banding_divides_the_width() -> None:
    for threshold in (0.2, 0.35, 0.5, 0.65, 0.85):
        config = solve_banding(threshold, 128)
        assert config.permutations % config.bands == 0


def test_the_seed_is_carried_through() -> None:
    assert solve_banding(0.5, 64, seed=99).seed == 99


def test_an_impossible_threshold_is_rejected() -> None:
    with pytest.raises(ConfigError, match="strictly inside"):
        solve_banding(0.0)
    with pytest.raises(ConfigError, match="strictly inside"):
        solve_banding(1.0)

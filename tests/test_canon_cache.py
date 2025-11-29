from __future__ import annotations

import pytest

from lexdrift.canon.cache import CachingPipeline
from lexdrift.canon.profiles import build_pipeline, profile
from lexdrift.config import CanonConfig
from lexdrift.errors import ConfigError

ACT = (
    "(a)  The board shall publish a schedule under W.S. 41-3-101 not later than "
    "sixty (60) days after the close of each irrigation season."
)


def cached(**kwargs: int) -> CachingPipeline:
    return CachingPipeline(build_pipeline(), **kwargs)


def test_the_first_call_is_a_miss() -> None:
    pipeline = cached()
    pipeline.run(ACT)
    stats = pipeline.stats()
    assert stats.misses == 1
    assert stats.hits == 0
    assert stats.hit_rate == 0.0


def test_a_repeat_is_a_hit() -> None:
    pipeline = cached()
    first = pipeline.run(ACT)
    second = pipeline.run(ACT)
    assert second is first
    assert pipeline.stats().hits == 1
    assert pipeline.stats().hit_rate == pytest.approx(0.5)


def test_the_cached_result_is_correct() -> None:
    pipeline = cached()
    plain = build_pipeline().run(ACT)
    assert pipeline.run(ACT).text == plain.text
    assert pipeline.run(ACT).text == plain.text


def test_a_different_config_is_a_different_entry() -> None:
    pipeline = cached()
    default = pipeline.run(ACT)
    verbatim = pipeline.run(ACT, profile("verbatim"))
    assert default.text != verbatim.text
    assert pipeline.stats().misses == 2
    assert pipeline.stats().hits == 0


def test_a_different_text_is_a_different_entry() -> None:
    pipeline = cached()
    pipeline.run(ACT)
    pipeline.run(ACT.replace("sixty", "ninety"))
    assert pipeline.stats().entries == 2


def test_the_rule_names_pass_through() -> None:
    assert cached().rule_names == build_pipeline().rule_names


def test_a_tiny_limit_evicts() -> None:
    pipeline = cached(limit_bytes=len(ACT) * 2 + 8)
    pipeline.run(ACT)
    pipeline.run(ACT.replace("sixty", "ninety"))
    assert pipeline.stats().entries == 1


def test_an_entry_larger_than_the_limit_is_not_stored() -> None:
    pipeline = cached(limit_bytes=8)
    pipeline.run(ACT)
    assert pipeline.stats().entries == 0
    assert pipeline.stats().bytes_held == 0


def test_eviction_keeps_the_most_recently_used() -> None:
    other = ACT.replace("sixty", "ninety")
    pipeline = cached(limit_bytes=len(ACT) * 2 + 8)
    pipeline.run(ACT)
    pipeline.run(ACT)
    pipeline.run(other)
    assert pipeline.stats().entries == 1
    before = pipeline.stats().misses
    pipeline.run(other)
    assert pipeline.stats().misses == before


def test_clear_empties_the_cache() -> None:
    pipeline = cached()
    pipeline.run(ACT)
    pipeline.clear()
    assert pipeline.stats().entries == 0
    assert pipeline.stats().bytes_held == 0


def test_a_negative_limit_is_rejected() -> None:
    with pytest.raises(ConfigError, match="cannot be negative"):
        CachingPipeline(build_pipeline(), limit_bytes=-1)


def test_an_explicit_config_is_honoured() -> None:
    pipeline = cached()
    plain = pipeline.run(ACT, CanonConfig(fold_case=False))
    assert "The board" in plain.text

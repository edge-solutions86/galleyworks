from __future__ import annotations

import pytest

from lexdrift.util.timing import Stopwatch, Timing


def test_a_new_stopwatch_is_empty() -> None:
    watch = Stopwatch()
    assert len(watch) == 0
    assert watch.total == 0.0
    assert watch.timings() == []


def test_a_timed_block_is_recorded() -> None:
    watch = Stopwatch()
    with watch.stage("ingest"):
        sum(range(1000))
    assert "ingest" in watch
    assert watch.total >= 0.0
    assert watch.timings()[0].calls == 1


def test_repeated_stages_accumulate() -> None:
    watch = Stopwatch()
    for _ in range(3):
        with watch.stage("match"):
            pass
    timing = watch.timings()[0]
    assert timing.calls == 3
    assert timing.name == "match"


def test_a_raising_block_is_still_recorded() -> None:
    watch = Stopwatch()
    with pytest.raises(ValueError, match="boom"), watch.stage("index"):
        raise ValueError("boom")
    assert "index" in watch


def test_direct_records_are_added() -> None:
    watch = Stopwatch()
    watch.record("canon", 1.5)
    watch.record("canon", 0.5)
    assert watch.total == pytest.approx(2.0)
    assert watch.timings()[0].average == pytest.approx(1.0)


def test_a_negative_record_is_rejected() -> None:
    with pytest.raises(ValueError, match="cannot be negative"):
        Stopwatch().record("canon", -1.0)


def test_stages_come_back_slowest_first() -> None:
    watch = Stopwatch()
    watch.record("fast", 0.1)
    watch.record("slow", 2.0)
    watch.record("middle", 1.0)
    assert [timing.name for timing in watch.timings()] == ["slow", "middle", "fast"]


def test_ties_break_on_the_name() -> None:
    watch = Stopwatch()
    watch.record("b", 1.0)
    watch.record("a", 1.0)
    assert [timing.name for timing in watch.timings()] == ["a", "b"]


def test_the_report_ends_with_a_total() -> None:
    watch = Stopwatch()
    watch.record("canon", 1.0)
    report = watch.report()
    assert report[0].startswith("canon: 1.000s over 1 calls")
    assert report[-1] == "total: 1.000s"


def test_reset_clears_everything() -> None:
    watch = Stopwatch()
    watch.record("canon", 1.0)
    watch.reset()
    assert len(watch) == 0
    assert watch.total == 0.0


def test_a_timing_with_no_calls_averages_zero() -> None:
    assert Timing(name="x", seconds=0.0, calls=0).average == 0.0


def test_a_timing_renders_itself() -> None:
    assert str(Timing(name="canon", seconds=0.5, calls=2)) == "canon: 0.500s over 2 calls"

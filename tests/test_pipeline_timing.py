from __future__ import annotations

from pathlib import Path

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.canon.cache import CachingPipeline
from lexdrift.canon.pipeline import Canonicalizer, CanonPipeline
from lexdrift.canon.profiles import build_pipeline
from lexdrift.corpus import discover
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.pipeline import Engine


def corpus_dir(tmp_path: Path, count: int = 3) -> Path:
    for position in range(count):
        instrument = make_bill(jurisdiction="wy", number=f"HB{position:04d}", body=MODEL_CLAUSE)
        (tmp_path / f"wy_2025_HB{position:04d}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    return tmp_path


def test_both_pipelines_satisfy_the_protocol() -> None:
    assert isinstance(build_pipeline(), Canonicalizer)
    assert isinstance(CachingPipeline(build_pipeline()), Canonicalizer)


def test_the_fingerprinter_caches_by_default() -> None:
    assert isinstance(Fingerprinter().pipeline, CachingPipeline)


def test_a_plain_pipeline_can_be_supplied() -> None:
    plain = build_pipeline()
    assert isinstance(Fingerprinter(pipeline=plain).pipeline, CanonPipeline)


def test_repeated_sections_hit_the_cache() -> None:
    fingerprinter = Fingerprinter()
    first = make_bill(jurisdiction="wy", number="HB0001", body=MODEL_CLAUSE)
    second = make_bill(jurisdiction="wy", number="HB0001", body=MODEL_CLAUSE)
    fingerprinter.fingerprint(first)
    fingerprinter.fingerprint(second)
    cache = fingerprinter.pipeline
    assert isinstance(cache, CachingPipeline)
    assert cache.stats().hits > 0


def test_caching_does_not_change_the_fingerprints() -> None:
    instrument = make_bill(jurisdiction="wy", number="HB0001", body=MODEL_CLAUSE)
    cached = Fingerprinter().fingerprint(instrument)
    plain = Fingerprinter(pipeline=build_pipeline()).fingerprint(instrument)
    assert [record.signature for record in cached] == [record.signature for record in plain]
    assert [record.selected for record in cached] == [record.selected for record in plain]


def test_a_run_records_every_stage(tmp_path: Path) -> None:
    engine = Engine()
    result = engine.run(discover(corpus_dir(tmp_path)))
    stages = {timing.name for timing in engine.stopwatch.timings()}
    assert {"ingest", "fingerprint", "index", "match", "report", "diffusion"} <= stages
    assert result.timings
    assert result.timings[-1].startswith("total:")


def test_the_stopwatch_totals_are_non_negative(tmp_path: Path) -> None:
    engine = Engine()
    engine.run(discover(corpus_dir(tmp_path)))
    assert engine.stopwatch.total >= 0.0
    assert all(timing.seconds >= 0.0 for timing in engine.stopwatch.timings())


def test_call_counts_follow_the_corpus_size(tmp_path: Path) -> None:
    engine = Engine()
    engine.run(discover(corpus_dir(tmp_path, count=4)))
    by_name = {timing.name: timing for timing in engine.stopwatch.timings()}
    assert by_name["ingest"].calls == 1
    assert by_name["fingerprint"].calls == 4
    assert by_name["match"].calls == 4

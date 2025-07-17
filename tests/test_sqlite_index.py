from __future__ import annotations

from pathlib import Path

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.config import EngineConfig, ShingleConfig
from lexdrift.errors import StoreError
from lexdrift.index.base import SegmentRef
from lexdrift.index.memory import MemoryIndex
from lexdrift.index.store import SqliteIndex


def wyoming() -> list:
    return fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE))


def montana() -> list:
    return fingerprints(make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE))


def unrelated() -> list:
    return fingerprints(make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE))


@pytest.fixture
def store(tmp_path: Path) -> SqliteIndex:
    return SqliteIndex(tmp_path / "index.sqlite")


def test_records_round_trip(store: SqliteIndex) -> None:
    record = wyoming()[0]
    ref = store.add(record)
    read_back = store.record(ref)
    assert read_back.key == record.key
    assert read_back.signature == record.signature
    assert read_back.selected == record.selected


def test_length_counts_segments(store: SqliteIndex) -> None:
    added = store.add_all(wyoming())
    assert len(store) == added


def test_an_unknown_reference_is_an_error(store: SqliteIndex) -> None:
    with pytest.raises(StoreError, match="no such segment"):
        store.record(SegmentRef(key=wyoming()[0].key, segment_index=99))


def test_adding_the_same_segment_twice_replaces_it(store: SqliteIndex) -> None:
    record = wyoming()[0]
    store.add(record)
    store.add(record)
    assert len(store) == 1


def test_a_copied_clause_is_a_candidate(store: SqliteIndex) -> None:
    store.add_all(wyoming())
    probe = next(r for r in montana() if r.path == "1(a)")
    assert store.candidates(probe)


def test_an_unrelated_clause_is_not_a_candidate(store: SqliteIndex) -> None:
    store.add_all(wyoming())
    probe = next(r for r in unrelated() if r.path == "1(a)")
    assert store.candidates(probe) == []


def test_the_store_agrees_with_the_memory_index() -> None:
    memory = MemoryIndex()
    memory.add_all(wyoming())
    with SqliteIndex(":memory:") as store:
        store.add_all(wyoming())
        probe = next(r for r in montana() if r.path == "1(a)")
        assert store.candidates(probe) == memory.candidates(probe)
        value = wyoming()[0].fingerprint_values[0]
        assert store.postings(value) == memory.postings(value)
        assert store.instruments() == memory.instruments()


def test_removing_an_instrument_clears_its_rows(store: SqliteIndex) -> None:
    records = wyoming()
    store.add_all(records)
    value = records[0].fingerprint_values[0]
    assert store.remove_instrument(records[0].key) == len(records)
    assert len(store) == 0
    assert store.postings(value) == []
    assert store.document_frequency(value) == 0


def test_registering_an_instrument_keeps_its_metadata(store: SqliteIndex) -> None:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    store.register(instrument)
    store.register(instrument)
    assert store.instruments() == []


def test_stats_summarise_the_store(store: SqliteIndex) -> None:
    store.add_all(wyoming())
    store.add_all(unrelated())
    stats = store.stats()
    assert stats.instruments == 2
    assert stats.segments == len(store)
    assert stats.buckets > 0


def test_the_index_survives_reopening(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    with SqliteIndex(path) as store:
        store.add_all(wyoming())
        expected = len(store)
    with SqliteIndex(path) as reopened:
        assert len(reopened) == expected
        probe = next(r for r in montana() if r.path == "1(a)")
        assert reopened.candidates(probe)


def test_a_different_engine_config_is_refused(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    with SqliteIndex(path) as store:
        store.add_all(wyoming())
    other = EngineConfig(shingle=ShingleConfig(width=9))
    with pytest.raises(StoreError, match="different engine configuration"):
        SqliteIndex(path, other)


def test_a_signature_of_the_wrong_width_is_rejected(tmp_path: Path) -> None:
    config = EngineConfig()
    store = SqliteIndex(tmp_path / "index.sqlite", config)
    bad = wyoming()[0]
    store.config = EngineConfig.from_mapping(
        {**config.to_mapping(), "lsh": {"permutations": 64, "bands": 16}}
    )
    with pytest.raises(StoreError, match="does not match the index"):
        store.add(bad)
    store.close()


def test_iteration_yields_every_record(store: SqliteIndex) -> None:
    store.add_all(wyoming())
    assert len(list(store)) == len(store)

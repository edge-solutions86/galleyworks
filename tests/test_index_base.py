from __future__ import annotations

from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.config import EngineConfig, LshConfig
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.index.base import Index, SegmentRef, open_index
from lexdrift.index.memory import MemoryIndex
from lexdrift.index.store import SqliteIndex


def wyoming() -> list:
    return fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE))


def test_both_backends_satisfy_the_protocol() -> None:
    assert isinstance(MemoryIndex(), Index)
    with SqliteIndex(":memory:") as store:
        assert isinstance(store, Index)


def test_open_index_without_a_path_is_in_memory() -> None:
    assert isinstance(open_index(), MemoryIndex)


def test_open_index_with_a_path_is_durable(tmp_path: Path) -> None:
    index = open_index(tmp_path / "index.sqlite")
    assert isinstance(index, SqliteIndex)
    index.close()


def test_open_index_passes_the_config_through() -> None:
    config = EngineConfig(lsh=LshConfig(permutations=64, bands=16))
    assert open_index(config=config).config == config


def test_segment_refs_round_trip_through_their_slug() -> None:
    ref = SegmentRef(key=InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"), segment_index=7)
    assert ref.slug == "wy/2025/lower/HB0142#7"
    assert SegmentRef.parse(ref.slug) == ref


def test_segment_refs_sort_by_key_then_index() -> None:
    key = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")
    refs = [SegmentRef(key, 4), SegmentRef(key, 1)]
    assert sorted(refs) == [SegmentRef(key, 1), SegmentRef(key, 4)]


def test_a_ref_can_be_built_from_a_record() -> None:
    record = wyoming()[0]
    assert SegmentRef.of(record).key == record.key


@pytest.mark.parametrize("durable", [False, True])
def test_the_two_backends_answer_alike(durable: bool, tmp_path: Path) -> None:
    index = open_index(tmp_path / "index.sqlite" if durable else None)
    records = wyoming()
    assert index.add_all(records) == len(records)
    assert len(index) == len(records)
    assert index.instruments() == [records[0].key]
    ref = SegmentRef.of(records[0])
    assert index.record(ref).path == records[0].path
    assert index.stats().segments == len(records)
    assert index.remove_instrument(records[0].key) == len(records)
    assert len(index) == 0

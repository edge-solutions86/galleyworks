from __future__ import annotations

from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from golden_corpus import golden_instruments
from lexdrift.errors import StoreError
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.index.base import SegmentRef
from lexdrift.index.maintenance import check_integrity
from lexdrift.index.store import SqliteIndex


@pytest.fixture
def store(tmp_path: Path) -> SqliteIndex:
    return SqliteIndex(tmp_path / "index.sqlite")


def records() -> list:
    return fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE))


def test_a_single_add_still_commits(store: SqliteIndex) -> None:
    ref = store.add(records()[0])
    assert store.record(ref).path
    assert len(store) == 1


def test_add_all_writes_everything(store: SqliteIndex) -> None:
    written = store.add_all(records())
    assert written == len(store) > 1


def test_a_bulk_block_commits_at_the_end(store: SqliteIndex) -> None:
    with store.bulk():
        for record in records():
            store.add(record)
    assert len(store) == len(records())


def test_a_failing_bulk_block_rolls_back(store: SqliteIndex) -> None:
    with pytest.raises(RuntimeError, match="boom"), store.bulk():
        store.add(records()[0])
        raise RuntimeError("boom")
    assert len(store) == 0


def test_bulk_blocks_cannot_nest(store: SqliteIndex) -> None:
    with store.bulk(), pytest.raises(StoreError, match="cannot be nested"), store.bulk():
        pass


def test_the_index_is_consistent_after_a_bulk_load(store: SqliteIndex) -> None:
    fingerprinter = Fingerprinter()
    with store.bulk():
        for instrument in golden_instruments():
            for record in fingerprinter.fingerprint(instrument):
                store.add(record)
    assert check_integrity(store).is_clean
    assert len(store.instruments()) == 4


def test_a_bad_signature_width_still_fails(tmp_path: Path) -> None:
    from lexdrift.config import EngineConfig

    store = SqliteIndex(tmp_path / "index.sqlite")
    store.config = EngineConfig.from_mapping(
        {**store.config.to_mapping(), "lsh": {"permutations": 64, "bands": 16}}
    )
    with pytest.raises(StoreError, match="does not match the index"):
        store.add(records()[0])
    store.close()


def test_a_replacement_inside_a_bulk_block(store: SqliteIndex) -> None:
    first = records()[0]
    with store.bulk():
        store.add(first)
        store.add(first)
    assert len(store) == 1
    assert store.record(SegmentRef.of(first)).path == first.path


def test_the_data_survives_reopening(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    with SqliteIndex(path) as store:
        store.add_all(records())
        expected = len(store)
    with SqliteIndex(path) as reopened:
        assert len(reopened) == expected


def test_an_empty_bulk_block_is_harmless(store: SqliteIndex) -> None:
    with store.bulk():
        pass
    assert len(store) == 0

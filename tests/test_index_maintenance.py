from __future__ import annotations

from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.maintenance import check_integrity, compact
from lexdrift.index.store import SqliteIndex


@pytest.fixture
def store(tmp_path: Path) -> SqliteIndex:
    index = SqliteIndex(tmp_path / "index.sqlite")
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    return index


def test_a_fresh_index_is_clean(store: SqliteIndex) -> None:
    report = check_integrity(store)
    assert report.is_clean
    assert report.segments > 0
    assert "no problems found" in report.summary()


def test_an_empty_index_is_clean(tmp_path: Path) -> None:
    with SqliteIndex(tmp_path / "empty.sqlite") as index:
        assert check_integrity(index).is_clean


def test_an_orphan_band_is_found(store: SqliteIndex) -> None:
    store.connection.execute(
        "INSERT INTO band (band_key, slug, segment_index) "
        "VALUES (X'00', 'wy/2025/lower/HB0142', 99)"
    )
    report = check_integrity(store)
    assert report.orphan_bands == 1
    assert not report.is_clean
    assert "orphan bands" in report.summary()


def test_an_orphan_posting_is_found(store: SqliteIndex) -> None:
    store.connection.execute(
        "INSERT INTO posting (value, slug, segment_index) VALUES (7, 'wy/2025/lower/HB0142', 99)"
    )
    assert check_integrity(store).orphan_postings == 1


def test_a_missing_band_is_found(store: SqliteIndex) -> None:
    store.connection.execute("DELETE FROM band WHERE rowid = (SELECT MIN(rowid) FROM band)")
    assert check_integrity(store).missing_bands == 1


def test_a_missing_posting_is_found(store: SqliteIndex) -> None:
    store.connection.execute("DELETE FROM posting WHERE rowid = (SELECT MIN(rowid) FROM posting)")
    assert check_integrity(store).missing_postings == 1


def test_an_unreadable_record_is_found(store: SqliteIndex) -> None:
    store.connection.execute("UPDATE segment SET blob = X'00'")
    report = check_integrity(store)
    assert report.unreadable == report.segments
    assert not report.is_clean


def test_compaction_repairs_a_missing_band(store: SqliteIndex) -> None:
    store.connection.execute("DELETE FROM band")
    assert check_integrity(store).missing_bands > 0
    compact(store)
    assert check_integrity(store).is_clean


def test_compaction_removes_orphans(store: SqliteIndex) -> None:
    store.connection.execute(
        "INSERT INTO posting (value, slug, segment_index) VALUES (7, 'wy/2025/lower/HB0142', 99)"
    )
    compact(store)
    assert check_integrity(store).is_clean


def test_compaction_reports_what_it_did(store: SqliteIndex) -> None:
    report = compact(store)
    assert report.rebuilt_segments == len(store)
    assert report.removed_bands > 0
    assert report.bytes_before > 0
    assert report.bytes_reclaimed >= 0


def test_the_index_still_answers_after_compaction(store: SqliteIndex) -> None:
    compact(store)
    probe = next(
        record
        for record in fingerprints(make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE))
        if record.path == "1(a)"
    )
    assert store.candidates(probe)


def test_compacting_an_empty_index(tmp_path: Path) -> None:
    with SqliteIndex(tmp_path / "empty.sqlite") as index:
        report = compact(index)
        assert report.rebuilt_segments == 0

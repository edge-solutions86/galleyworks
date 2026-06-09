from __future__ import annotations

import io
from dataclasses import replace
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, main
from lexdrift.fingerprint.codec import encode_record
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.index.maintenance import check_integrity
from lexdrift.index.prune import find_prunable, prune
from lexdrift.index.store import SqliteIndex


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def store(tmp_path: Path) -> SqliteIndex:
    index = SqliteIndex(tmp_path / "index.sqlite")
    fingerprinter = Fingerprinter()
    with index.bulk():
        for instrument in golden_instruments()[:2]:
            index.register(instrument)
            for record in fingerprinter.fingerprint(instrument):
                index.add(record)
    return index


def blank_one_segment(store: SqliteIndex) -> str:
    row = store.connection.execute(
        "SELECT slug, segment_index, blob FROM segment LIMIT 1"
    ).fetchone()
    from lexdrift.fingerprint.codec import decode_record

    record = replace(decode_record(row["blob"]), selected=())
    store.connection.execute(
        "UPDATE segment SET blob = ? WHERE slug = ? AND segment_index = ?",
        (encode_record(record), row["slug"], row["segment_index"]),
    )
    return f"{row['slug']}#{row['segment_index']}"


def test_a_healthy_index_has_nothing_to_prune(store: SqliteIndex) -> None:
    report = find_prunable(store)
    assert report.is_clean
    assert report.summary() == "nothing to prune"
    assert report.total == 0


def test_a_segment_with_no_fingerprints_is_found(store: SqliteIndex) -> None:
    slug = blank_one_segment(store)
    report = find_prunable(store)
    assert report.empty_segments == (slug,)
    assert not report.is_clean


def test_an_instrument_with_no_segments_is_found(store: SqliteIndex) -> None:
    store.connection.execute("DELETE FROM segment WHERE slug = 'wy/2023/lower/HB0142'")
    assert "wy/2023/lower/HB0142" in find_prunable(store).empty_instruments


def test_pruning_removes_the_segment(store: SqliteIndex) -> None:
    blank_one_segment(store)
    before = len(store)
    assert prune(store).total == 1
    assert len(store) == before - 1
    assert find_prunable(store).is_clean


def test_pruning_removes_the_instrument_row(store: SqliteIndex) -> None:
    store.connection.execute("DELETE FROM segment WHERE slug = 'wy/2023/lower/HB0142'")
    prune(store)
    rows = store.connection.execute("SELECT COUNT(*) AS n FROM instrument").fetchone()
    assert int(rows["n"]) == 1


def test_pruning_leaves_the_index_consistent(store: SqliteIndex) -> None:
    blank_one_segment(store)
    prune(store)
    assert check_integrity(store).is_clean


def test_pruning_a_clean_index_changes_nothing(store: SqliteIndex) -> None:
    before = len(store)
    assert prune(store).is_clean
    assert len(store) == before


def test_the_summary_counts_both_kinds(store: SqliteIndex) -> None:
    blank_one_segment(store)
    assert "segments with no fingerprints" in find_prunable(store).summary()


def test_the_check_command_reports_prunable_rows(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    with SqliteIndex(path) as index:
        index.add_all(Fingerprinter().fingerprint(golden_instruments()[0]))
    code, output = run("check", "--index", str(path))
    assert code == EXIT_OK
    assert "nothing to prune" in output


def test_the_check_command_can_prune(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    with SqliteIndex(path) as index:
        index.add_all(Fingerprinter().fingerprint(golden_instruments()[0]))
        blank_one_segment(index)
    code, output = run("check", "--prune", "--index", str(path))
    assert code in {EXIT_OK, EXIT_FINDINGS}
    assert "pruned 1 rows" in output

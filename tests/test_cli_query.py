from __future__ import annotations

import csv
import io
import json
from pathlib import Path

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_FINDINGS, EXIT_OK, main


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def prepared(tmp_path: Path) -> tuple[str, str, str]:
    root = tmp_path / "bills"
    root.mkdir()
    earlier = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    (root / "wy_2025_HB0142.txt").write_text(earlier.raw_text, encoding="utf-8")
    index_path = tmp_path / "index.sqlite"
    run("index", "--index", str(index_path), str(root))

    copy = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    copy_path = tmp_path / "candidate.txt"
    copy_path.write_text(copy.raw_text, encoding="utf-8")

    original = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    original_path = tmp_path / "original.txt"
    original_path.write_text(original.raw_text, encoding="utf-8")
    return str(index_path), str(copy_path), str(original_path)


def test_a_copied_bill_is_reported(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    code, output = run("query", "--index", index_path, "--jurisdiction", "mt", copy_path)
    assert code == EXIT_OK
    assert "lexdrift report for mt/2025/lower/HB0311" in output
    assert "wy/2025/lower/HB0142" in output


def test_an_original_bill_reports_nothing(prepared: tuple[str, str, str]) -> None:
    index_path, _, original_path = prepared
    code, output = run("query", "--index", index_path, "--jurisdiction", "co", original_path)
    assert code == EXIT_OK
    assert "no reuse found" in output


def test_json_output_parses(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    _, output = run(
        "query", "--index", index_path, "--jurisdiction", "mt", "--output", "json", copy_path
    )
    payload = json.loads(output)
    assert payload["instrument"]["jurisdiction"] == "mt"
    assert payload["matches"]


def test_csv_output_parses(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    _, output = run(
        "query", "--index", index_path, "--jurisdiction", "mt", "--output", "csv", copy_path
    )
    rows = list(csv.DictReader(io.StringIO(output)))
    assert rows
    assert rows[0]["other_jurisdiction"] == "wy"


def test_the_fail_threshold_flags_a_copy(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    code, _ = run(
        "query", "--index", index_path, "--jurisdiction", "mt", "--fail-over", "0.1", copy_path
    )
    assert code == EXIT_FINDINGS


def test_the_fail_threshold_passes_an_original(prepared: tuple[str, str, str]) -> None:
    index_path, _, original_path = prepared
    code, _ = run(
        "query", "--index", index_path, "--jurisdiction", "co", "--fail-over", "0.1", original_path
    )
    assert code == EXIT_OK


def test_the_queried_document_is_not_added_to_the_index(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    run("query", "--index", index_path, "--jurisdiction", "mt", copy_path)
    from lexdrift.index.store import SqliteIndex

    with SqliteIndex(index_path) as store:
        assert [key.jurisdiction for key in store.instruments()] == ["wy"]


def test_hints_can_be_overridden(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    _, output = run(
        "query",
        "--index",
        index_path,
        "--jurisdiction",
        "mt",
        "--session",
        "2026",
        "--number",
        "HB9999",
        copy_path,
    )
    assert "mt/2026/lower/HB9999" in output


def test_a_missing_document_is_an_error(prepared: tuple[str, str, str]) -> None:
    index_path, _, _ = prepared
    code, _ = run("query", "--index", index_path, "--jurisdiction", "mt", "absent.txt")
    assert code == EXIT_ERROR


def test_the_width_flag_narrows_the_report(prepared: tuple[str, str, str]) -> None:
    index_path, copy_path, _ = prepared
    _, output = run(
        "query", "--index", index_path, "--jurisdiction", "mt", "--width", "60", copy_path
    )
    assert all(len(line) <= 60 for line in output.splitlines())

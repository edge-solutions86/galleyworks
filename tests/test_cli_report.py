from __future__ import annotations

import io
import json
from pathlib import Path

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, main
from lexdrift.cli.report_cmd import file_name_for
from lexdrift.errors import ConfigError
from lexdrift.identity import Chamber, InstrumentKey


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def corpus(tmp_path: Path) -> str:
    root = tmp_path / "bills"
    root.mkdir()
    bills = (
        ("wy", "HB0142", MODEL_CLAUSE),
        ("mt", "HB0311", MODEL_CLAUSE),
        ("co", "SB0007", LOCAL_CLAUSE),
    )
    for jurisdiction, number, body in bills:
        instrument = make_bill(jurisdiction=jurisdiction, number=number, body=body)
        (root / f"{jurisdiction}_2025_{number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    return str(root)


def test_file_names_are_filesystem_safe() -> None:
    key = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")
    assert file_name_for(key, "text") == "wy-2025-lower-HB0142.txt"
    assert file_name_for(key, "json").endswith(".json")


def test_an_unknown_format_is_rejected() -> None:
    key = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")
    with pytest.raises(ConfigError, match="unknown output format"):
        file_name_for(key, "yaml")


def test_a_corpus_with_reuse_is_a_finding(corpus: str) -> None:
    code, output = run("report", corpus)
    assert code == EXIT_FINDINGS
    assert "3 instruments" in output


def test_reports_are_printed_to_the_stream(corpus: str) -> None:
    _, output = run("report", corpus)
    assert "lexdrift report for" in output
    assert "no reuse found" in output


def test_only_findings_hides_the_clean_ones(corpus: str) -> None:
    _, output = run("report", "--only-findings", corpus)
    assert "no reuse found" not in output


def test_reports_can_be_written_to_a_directory(corpus: str, tmp_path: Path) -> None:
    out_dir = tmp_path / "reports"
    code, output = run("report", "--out", str(out_dir), corpus)
    assert code == EXIT_FINDINGS
    written = sorted(path.name for path in out_dir.iterdir())
    assert written == [
        "co-2025-lower-SB0007.txt",
        "mt-2025-lower-HB0311.txt",
        "wy-2025-lower-HB0142.txt",
    ]
    assert "3 reports written" in output


def test_json_reports_are_written(corpus: str, tmp_path: Path) -> None:
    out_dir = tmp_path / "reports"
    run("report", "--out", str(out_dir), "--output", "json", corpus)
    payload = json.loads((out_dir / "wy-2025-lower-HB0142.json").read_text(encoding="utf-8"))
    assert payload["instrument"]["jurisdiction"] == "wy"


def test_only_findings_limits_what_is_written(corpus: str, tmp_path: Path) -> None:
    out_dir = tmp_path / "reports"
    run("report", "--only-findings", "--out", str(out_dir), corpus)
    assert "co-2025-lower-SB0007.txt" not in {path.name for path in out_dir.iterdir()}


def test_a_clean_corpus_exits_zero(tmp_path: Path) -> None:
    root = tmp_path / "one"
    root.mkdir()
    instrument = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    (root / "co_2025_SB0007.txt").write_text(instrument.raw_text, encoding="utf-8")
    code, output = run("report", str(root))
    assert code == EXIT_OK
    assert "1 instruments, 0 with reuse" in output


def test_the_edge_count_is_reported(corpus: str) -> None:
    _, output = run("report", corpus)
    assert "edges" in output


def test_the_output_directory_is_created(corpus: str, tmp_path: Path) -> None:
    out_dir = tmp_path / "deep" / "reports"
    run("report", "--out", str(out_dir), corpus)
    assert out_dir.is_dir()

from __future__ import annotations

import io
from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_FINDINGS, EXIT_OK, EXIT_USAGE, main


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def corpus_dir(tmp_path: Path, *, broken: bool = False) -> Path:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    (tmp_path / "wy_2025_HB0142.txt").write_text(instrument.raw_text, encoding="utf-8")
    if broken:
        # An empty file is claimed by no reader at all, which is the ingest
        # failure a scraped corpus actually produces.
        (tmp_path / "mt_2025_HB0311.txt").write_text("   \n\n", encoding="utf-8")
    return tmp_path


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def test_no_command_prints_help() -> None:
    code, output = run()
    assert code == EXIT_USAGE
    assert "Usage: lexdrift" in output


def test_the_version_flag_reports_the_package_version() -> None:
    assert main(["--version"], io.StringIO()) == EXIT_OK


def test_ingest_lists_what_parsed(tmp_path: Path) -> None:
    code, output = run("ingest", str(corpus_dir(tmp_path)))
    assert code == EXIT_OK
    assert "parsed  wy/2025/lower/HB0142  enrolled" in output
    assert "1 parsed, 0 failed, 0 skipped" in output


def test_quiet_prints_only_the_totals(tmp_path: Path) -> None:
    code, output = run("ingest", "--quiet", str(corpus_dir(tmp_path)))
    assert code == EXIT_OK
    assert "parsed  wy" not in output
    assert output.strip().endswith("1 parsed, 0 failed, 0 skipped")


def test_a_document_that_does_not_parse_is_a_finding(tmp_path: Path) -> None:
    code, output = run("ingest", str(corpus_dir(tmp_path, broken=True)))
    assert code == EXIT_FINDINGS
    assert "failed  " in output
    assert "1 parsed, 1 failed, 0 skipped" in output


def test_skipped_files_are_named(tmp_path: Path) -> None:
    root = corpus_dir(tmp_path)
    (root / "notes.txt").write_text("scratch\n", encoding="utf-8")
    code, output = run("ingest", str(root))
    assert code == EXIT_OK
    assert "skipped" in output
    assert "1 parsed, 0 failed, 1 skipped" in output


def test_a_pinned_reader_is_used(tmp_path: Path) -> None:
    code, output = run("ingest", "--format", "plaintext", str(corpus_dir(tmp_path)))
    assert code == EXIT_OK
    assert "plaintext" in output


def test_a_manifest_is_recognised_by_its_suffix(tmp_path: Path) -> None:
    root = corpus_dir(tmp_path)
    manifest = root / "corpus.yaml"
    manifest.write_text(
        "documents:\n"
        "  - path: wy_2025_HB0142.txt\n"
        "    jurisdiction: wy\n"
        "    session: '2025'\n"
        "    number: HB0142\n",
        encoding="utf-8",
    )
    code, output = run("ingest", str(manifest))
    assert code == EXIT_OK
    assert "1 parsed" in output


def test_a_missing_directory_reports_a_stable_code(tmp_path: Path) -> None:
    code, _ = run("ingest", str(tmp_path / "absent"))
    assert code == EXIT_ERROR


def test_an_unknown_command_is_a_usage_error() -> None:
    assert main(["nonesuch"], io.StringIO()) == EXIT_USAGE

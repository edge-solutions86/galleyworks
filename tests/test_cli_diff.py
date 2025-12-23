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


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def write(tmp_path: Path, name: str, body: str) -> str:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=body)
    path = tmp_path / name
    path.write_text(instrument.raw_text, encoding="utf-8")
    return str(path)


@pytest.fixture
def pair(tmp_path: Path) -> tuple[str, str]:
    other = MODEL_CLAUSE.replace("priority date", "diversion point")
    return write(tmp_path, "left.txt", MODEL_CLAUSE), write(tmp_path, "right.txt", other)


def test_identical_documents_exit_zero(tmp_path: Path) -> None:
    path = write(tmp_path, "one.txt", MODEL_CLAUSE)
    code, output = run("diff", "--jurisdiction", "wy", path, path)
    assert code == EXIT_OK
    assert "0 changed" in output


def test_a_changed_document_is_a_finding(pair: tuple[str, str]) -> None:
    code, output = run("diff", "--jurisdiction", "wy", *pair)
    assert code == EXIT_FINDINGS
    assert "~ 1(a)" in output
    assert "changed" in output


def test_the_two_sides_are_named(pair: tuple[str, str]) -> None:
    _, output = run("diff", "--jurisdiction", "wy", *pair)
    assert output.startswith("wy/2025/lower/HB0142 -> wy/2025/lower/HB0142")


def test_the_summary_counts_the_churn(pair: tuple[str, str]) -> None:
    _, output = run("diff", "--jurisdiction", "wy", *pair)
    assert "words of churn" in output


def test_unchanged_segments_are_hidden_by_default(tmp_path: Path) -> None:
    extended = f"{MODEL_CLAUSE}\n\n(b)  A fee applies."
    left = write(tmp_path, "left.txt", MODEL_CLAUSE)
    right = write(tmp_path, "right.txt", extended)
    _, output = run("diff", "--jurisdiction", "wy", left, right)
    assert "unchanged" in output.splitlines()[-1]
    assert not any(line.startswith("  1(a)") for line in output.splitlines())


def test_unchanged_segments_can_be_shown(tmp_path: Path) -> None:
    extended = f"{MODEL_CLAUSE}\n\n(b)  A fee applies."
    left = write(tmp_path, "left.txt", MODEL_CLAUSE)
    right = write(tmp_path, "right.txt", extended)
    _, output = run("diff", "--show-unchanged", "--jurisdiction", "wy", left, right)
    assert any("unchanged" in line and "1(a)" in line for line in output.splitlines())


def test_an_added_segment_is_marked(tmp_path: Path) -> None:
    extended = f"{MODEL_CLAUSE}\n\n(b)  A fee applies."
    left = write(tmp_path, "left.txt", MODEL_CLAUSE)
    right = write(tmp_path, "right.txt", extended)
    _, output = run("diff", "--jurisdiction", "wy", left, right)
    assert "+ 1(b)" in output


def test_the_default_profile_is_verbatim(tmp_path: Path) -> None:
    left = write(tmp_path, "left.txt", MODEL_CLAUSE)
    right = write(tmp_path, "right.txt", MODEL_CLAUSE.replace("The board", "THE BOARD"))
    assert run("diff", "--jurisdiction", "wy", left, right)[0] == EXIT_FINDINGS


def test_a_looser_profile_ignores_case(tmp_path: Path) -> None:
    left = write(tmp_path, "left.txt", MODEL_CLAUSE)
    right = write(tmp_path, "right.txt", MODEL_CLAUSE.replace("The board", "THE BOARD"))
    code, _ = run("diff", "--profile", "default", "--jurisdiction", "wy", left, right)
    assert code == EXIT_OK


def test_an_unknown_profile_is_an_error(pair: tuple[str, str]) -> None:
    assert run("diff", "--profile", "nowhere", "--jurisdiction", "wy", *pair)[0] == EXIT_ERROR


def test_a_missing_document_is_an_error(tmp_path: Path) -> None:
    path = write(tmp_path, "one.txt", MODEL_CLAUSE)
    assert run("diff", "--jurisdiction", "wy", path, "absent.txt")[0] == EXIT_ERROR


def test_the_jurisdiction_is_required(pair: tuple[str, str]) -> None:
    assert run("diff", *pair)[0] == EXIT_USAGE

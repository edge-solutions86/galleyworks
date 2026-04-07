from __future__ import annotations

import io
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.explain_cmd import render_case
from lexdrift.cli.main import EXIT_ERROR, EXIT_FINDINGS, EXIT_OK, main
from lexdrift.pipeline import Engine


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
    instruments = golden_instruments()
    for instrument in instruments[:1]:
        key = instrument.key
        (root / f"{key.jurisdiction}_{key.session}_{key.number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    index_path = tmp_path / "index.sqlite"
    run("index", "--index", str(index_path), str(root))
    copy = tmp_path / "montana.txt"
    copy.write_text(instruments[1].raw_text, encoding="utf-8")
    original = tmp_path / "colorado.txt"
    original.write_text(instruments[3].raw_text, encoding="utf-8")
    return str(index_path), str(copy), str(original)


def test_the_case_starts_with_the_summary() -> None:
    result = Engine().run_over(golden_instruments())
    report = result.report_for("mt/2024/lower/HB0311")
    assert report is not None
    lines = render_case(report)
    assert lines[0].startswith("mt/2024/lower/HB0311:")
    assert "of the document reused" in lines[0]


def test_the_case_numbers_its_findings() -> None:
    result = Engine().run_over(golden_instruments())
    report = result.report_for("mt/2024/lower/HB0311")
    assert report is not None
    body = "\n".join(render_case(report))
    assert "1. " in body
    assert "evidence that" in body


def test_the_case_never_quotes_the_text() -> None:
    result = Engine().run_over(golden_instruments())
    report = result.report_for("mt/2024/lower/HB0311")
    assert report is not None
    body = "\n".join(render_case(report))
    assert "schedule of appropriations of record" not in body


def test_an_empty_report_says_so() -> None:
    result = Engine().run_over(golden_instruments())
    report = result.report_for("co/2025/upper/SB0007")
    assert report is not None
    assert "no reuse found" in "\n".join(render_case(report))


def test_the_command_explains_a_copy(prepared: tuple[str, str, str]) -> None:
    index_path, copy, _ = prepared
    code, output = run("explain", "--index", index_path, "--jurisdiction", "mt", copy)
    assert code in {EXIT_OK, EXIT_FINDINGS}
    assert "wy/2023/lower/HB0142" in output
    assert "evidence that" in output


def test_the_command_is_quiet_on_an_original(prepared: tuple[str, str, str]) -> None:
    index_path, _, original = prepared
    code, output = run("explain", "--index", index_path, "--jurisdiction", "co", original)
    assert code == EXIT_OK
    assert "no reuse found" in output


def test_the_display_filters_apply(prepared: tuple[str, str, str]) -> None:
    index_path, copy, _ = prepared
    _, output = run("explain", "--index", index_path, "--jurisdiction", "mt", "--top", "1", copy)
    assert output.count("evidence that") <= 1


def test_the_confidence_floor_applies(prepared: tuple[str, str, str]) -> None:
    index_path, copy, _ = prepared
    _, output = run(
        "explain",
        "--index",
        index_path,
        "--jurisdiction",
        "mt",
        "--min-confidence",
        "strong",
        copy,
    )
    assert "weak evidence" not in output


def test_a_missing_document_is_an_error(prepared: tuple[str, str, str]) -> None:
    index_path, _, _ = prepared
    code, _ = run("explain", "--index", index_path, "--jurisdiction", "mt", "absent.txt")
    assert code == EXIT_ERROR

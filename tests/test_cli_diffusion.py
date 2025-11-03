from __future__ import annotations

import io
from datetime import date
from pathlib import Path

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, main


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
        ("wy", "HB0142", MODEL_CLAUSE, date(2023, 7, 1)),
        ("mt", "HB0311", MODEL_CLAUSE, date(2024, 7, 1)),
        ("nd", "HB0044", MODEL_CLAUSE, date(2025, 7, 1)),
        ("co", "SB0007", LOCAL_CLAUSE, date(2025, 8, 1)),
    )
    for jurisdiction, number, body, enacted in bills:
        instrument = make_bill(jurisdiction=jurisdiction, number=number, body=body, enacted=enacted)
        (root / f"{jurisdiction}_2025_{number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    return str(root)


def test_a_family_is_reported(corpus: str) -> None:
    code, output = run("diffusion", corpus)
    assert code == EXIT_FINDINGS
    assert "family of 3" in output
    assert "earliest: wy/2025/lower/HB0142 (dated)" in output


def test_the_root_is_marked(corpus: str) -> None:
    _, output = run("diffusion", corpus)
    assert "* wy/2025/lower/HB0142" in output


def test_the_spread_is_measured(corpus: str) -> None:
    _, output = run("diffusion", corpus)
    assert "spread over 731 days" in output


def test_the_unrelated_bill_is_not_a_family(corpus: str) -> None:
    _, output = run("diffusion", corpus)
    assert "co/2025/lower/SB0007" not in output


def test_the_family_count_is_printed(corpus: str) -> None:
    _, output = run("diffusion", corpus)
    assert "1 families over" in output


def test_min_members_filters(corpus: str) -> None:
    code, output = run("diffusion", "--min-members", "9", corpus)
    assert code == EXIT_OK
    assert "0 families" in output


def test_crossing_only_keeps_the_family(corpus: str) -> None:
    _, output = run("diffusion", "--crossing-only", corpus)
    assert "family of 3" in output


def test_crossing_only_drops_a_single_jurisdiction_family(tmp_path: Path) -> None:
    root = tmp_path / "bills"
    root.mkdir()
    for number in ("HB0001", "HB0002"):
        instrument = make_bill(jurisdiction="wy", number=number, body=MODEL_CLAUSE)
        (root / f"wy_2025_{number}.txt").write_text(instrument.raw_text, encoding="utf-8")
    code, output = run("diffusion", "--crossing-only", str(root))
    assert code == EXIT_OK
    assert "0 families" in output


def test_reduction_lowers_the_edge_count(corpus: str) -> None:
    _, plain = run("diffusion", corpus)
    _, reduced = run("diffusion", "--reduce", corpus)
    plain_edges = int(plain.split("families over ")[1].split(" ")[0])
    reduced_edges = int(reduced.split("families over ")[1].split(" ")[0])
    assert reduced_edges <= plain_edges


def test_a_dot_file_is_written(corpus: str, tmp_path: Path) -> None:
    dot = tmp_path / "graph.dot"
    _, output = run("diffusion", "--dot", str(dot), corpus)
    assert dot.exists()
    assert dot.read_text(encoding="utf-8").startswith("digraph reuse {")
    assert "graph written to" in output


def test_a_corpus_with_no_reuse_exits_zero(tmp_path: Path) -> None:
    root = tmp_path / "bills"
    root.mkdir()
    instrument = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    (root / "co_2025_SB0007.txt").write_text(instrument.raw_text, encoding="utf-8")
    code, output = run("diffusion", str(root))
    assert code == EXIT_OK
    assert "0 families" in output

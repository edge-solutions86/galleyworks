from __future__ import annotations

import io
from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_FINDINGS, EXIT_OK, main
from lexdrift.index.store import SqliteIndex


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def index_path(tmp_path: Path) -> str:
    root = tmp_path / "bills"
    root.mkdir()
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    (root / "wy_2025_HB0142.txt").write_text(instrument.raw_text, encoding="utf-8")
    path = tmp_path / "index.sqlite"
    run("index", "--index", str(path), str(root))
    return str(path)


def break_bands(path: str) -> None:
    with SqliteIndex(path) as store:
        store.connection.execute("DELETE FROM band")


def test_a_healthy_index_passes(index_path: str) -> None:
    code, output = run("check", "--index", index_path)
    assert code == EXIT_OK
    assert "no problems found" in output


def test_a_broken_index_is_a_finding(index_path: str) -> None:
    break_bands(index_path)
    code, output = run("check", "--index", index_path)
    assert code == EXIT_FINDINGS
    assert "missing bands" in output
    assert "--repair" in output


def test_repair_fixes_the_index(index_path: str) -> None:
    break_bands(index_path)
    code, output = run("check", "--repair", "--index", index_path)
    assert code == EXIT_OK
    assert "rebuilt" in output
    assert output.strip().endswith("no problems found")


def test_repair_on_a_healthy_index_is_harmless(index_path: str) -> None:
    code, output = run("check", "--repair", "--index", index_path)
    assert code == EXIT_OK
    assert "reclaimed" in output


def test_the_index_still_answers_after_repair(index_path: str) -> None:
    break_bands(index_path)
    run("check", "--repair", "--index", index_path)
    copy = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    document = Path(index_path).parent / "copy.txt"
    document.write_text(copy.raw_text, encoding="utf-8")
    code, output = run("query", "--index", index_path, "--jurisdiction", "mt", str(document))
    assert code == EXIT_OK
    assert "wy/2025/lower/HB0142" in output


def test_a_missing_index_is_an_error(tmp_path: Path) -> None:
    code, _ = run("check", "--index", str(tmp_path / "missing" / "i.sqlite"))
    assert code == EXIT_ERROR


def test_a_mismatched_config_is_an_error(index_path: str, tmp_path: Path) -> None:
    config = tmp_path / "engine.yaml"
    config.write_text("shingle:\n  width: 9\n", encoding="utf-8")
    code, _ = run("check", "--index", index_path, "--config", str(config))
    assert code == EXIT_ERROR

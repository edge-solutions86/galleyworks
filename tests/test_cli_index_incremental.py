from __future__ import annotations

import io
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_OK, main
from lexdrift.index.store import SqliteIndex


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


@pytest.fixture
def corpus(tmp_path: Path) -> Path:
    root = tmp_path / "bills"
    root.mkdir()
    for instrument in golden_instruments():
        key = instrument.key
        name = f"{key.jurisdiction}_{key.session}_{key.number}.txt"
        (root / name).write_text(instrument.raw_text, encoding="utf-8")
    return root


def test_a_first_incremental_run_indexes_everything(corpus: Path, tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    code, output = run("index", "--incremental", "--index", str(path), str(corpus))
    assert code == EXIT_OK
    assert "0 unchanged, 0 changed, 6 new" in output
    assert "6 instruments" in output


def test_a_second_run_indexes_nothing(corpus: Path, tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    run("index", "--incremental", "--index", str(path), str(corpus))
    code, output = run("index", "--incremental", "--index", str(path), str(corpus))
    assert code == EXIT_OK
    assert "6 unchanged" in output
    assert "0 instruments, 0 segments indexed" in output


def test_only_the_changed_document_is_reindexed(corpus: Path, tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    run("index", "--incremental", "--index", str(path), str(corpus))
    target = corpus / "mt_2024_HB0311.txt"
    target.write_text(
        target.read_text(encoding="utf-8").replace("sixty (60)", "ninety (90)"),
        encoding="utf-8",
    )
    _, output = run("index", "--incremental", "--index", str(path), str(corpus))
    assert "5 unchanged, 1 changed" in output
    assert "indexed mt/2024/lower/HB0311" in output
    assert "indexed wy/2023/lower/HB0142" not in output


def test_a_new_document_is_picked_up(corpus: Path, tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    run("index", "--incremental", "--index", str(path), str(corpus))
    source = (corpus / "wy_2023_HB0142.txt").read_text(encoding="utf-8")
    (corpus / "ut_2025_HB0500.txt").write_text(source, encoding="utf-8")
    _, output = run("index", "--incremental", "--index", str(path), str(corpus))
    assert "1 new" in output
    assert "indexed ut/2025/lower/HB0500" in output


def test_a_removed_document_is_reported(corpus: Path, tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    run("index", "--incremental", "--index", str(path), str(corpus))
    (corpus / "co_2025_SB0007.txt").unlink()
    _, output = run("index", "--incremental", "--index", str(path), str(corpus))
    assert "1 no longer present" in output


def test_the_index_still_answers_after_an_incremental_run(corpus: Path, tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    run("index", "--incremental", "--index", str(path), str(corpus))
    run("index", "--incremental", "--index", str(path), str(corpus))
    with SqliteIndex(path) as store:
        assert len(store.instruments()) == 6
        assert len(store) > 4


def test_incremental_needs_a_durable_index(corpus: Path) -> None:
    code, _ = run("index", "--incremental", str(corpus))
    assert code == EXIT_ERROR


def test_a_plain_run_still_works(corpus: Path, tmp_path: Path) -> None:
    code, output = run("index", "--index", str(tmp_path / "i.sqlite"), str(corpus))
    assert code == EXIT_OK
    assert "6 instruments" in output
    assert "unchanged" not in output

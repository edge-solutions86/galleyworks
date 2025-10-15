from __future__ import annotations

import io
from pathlib import Path

import pytest

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_OK, main
from lexdrift.config import EngineConfig
from lexdrift.index.store import SqliteIndex


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def corpus_dir(tmp_path: Path) -> Path:
    root = tmp_path / "bills"
    root.mkdir()
    for jurisdiction, number in (("wy", "HB0142"), ("mt", "HB0311")):
        instrument = make_bill(jurisdiction=jurisdiction, number=number, body=MODEL_CLAUSE)
        (root / f"{jurisdiction}_2025_{number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    return root


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def test_indexing_writes_a_database(tmp_path: Path) -> None:
    index_path = tmp_path / "index.sqlite"
    code, output = run("index", "--index", str(index_path), str(corpus_dir(tmp_path)))
    assert code == EXIT_OK
    assert index_path.exists()
    assert "2 instruments" in output


def test_the_database_holds_the_segments(tmp_path: Path) -> None:
    index_path = tmp_path / "index.sqlite"
    run("index", "--index", str(index_path), str(corpus_dir(tmp_path)))
    with SqliteIndex(index_path) as store:
        assert len(store) > 0
        assert len(store.instruments()) == 2


def test_each_instrument_is_named(tmp_path: Path) -> None:
    _, output = run("index", "--index", str(tmp_path / "i.sqlite"), str(corpus_dir(tmp_path)))
    assert "indexed wy/2025/lower/HB0142" in output
    assert "indexed mt/2025/lower/HB0311" in output


def test_indexing_twice_does_not_double_the_index(tmp_path: Path) -> None:
    index_path = tmp_path / "index.sqlite"
    root = corpus_dir(tmp_path)
    run("index", "--index", str(index_path), str(root))
    with SqliteIndex(index_path) as store:
        first = len(store)
    run("index", "--index", str(index_path), str(root))
    with SqliteIndex(index_path) as store:
        assert len(store) == first


def test_replace_clears_before_writing(tmp_path: Path) -> None:
    index_path = tmp_path / "index.sqlite"
    root = corpus_dir(tmp_path)
    run("index", "--index", str(index_path), str(root))
    code, _ = run("index", "--replace", "--index", str(index_path), str(root))
    assert code == EXIT_OK
    with SqliteIndex(index_path) as store:
        assert len(store.instruments()) == 2


def test_stats_are_printed_on_request(tmp_path: Path) -> None:
    _, output = run(
        "index", "--stats", "--index", str(tmp_path / "i.sqlite"), str(corpus_dir(tmp_path))
    )
    assert "index now holds 2 instruments" in output
    assert "engine digest" in output


def test_an_in_memory_index_is_allowed(tmp_path: Path) -> None:
    code, output = run("index", str(corpus_dir(tmp_path)))
    assert code == EXIT_OK
    assert "2 instruments" in output


def test_a_profile_changes_the_digest(tmp_path: Path) -> None:
    root = corpus_dir(tmp_path)
    _, plain = run("index", "--stats", str(root))
    _, verbatim = run("index", "--stats", "--profile", "verbatim", str(root))
    assert plain != verbatim


def test_an_unknown_profile_is_an_error(tmp_path: Path) -> None:
    code, _ = run("index", "--profile", "nowhere", str(corpus_dir(tmp_path)))
    assert code == EXIT_ERROR


def test_a_config_file_is_honoured(tmp_path: Path) -> None:
    config = tmp_path / "engine.yaml"
    config.write_text("shingle:\n  width: 7\n", encoding="utf-8")
    _, output = run("index", "--stats", "--config", str(config), str(corpus_dir(tmp_path)))
    expected = EngineConfig.from_mapping({"shingle": {"width": 7}}).digest()
    assert expected in output

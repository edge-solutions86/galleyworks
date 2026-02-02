from __future__ import annotations

import io
import json
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_ERROR, EXIT_OK, EXIT_USAGE, main
from lexdrift.index.memory import MemoryIndex
from lexdrift.index.statistics import CorpusStats, describe
from lexdrift.pipeline import Engine


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def loaded() -> MemoryIndex:
    engine = Engine()
    for instrument in golden_instruments():
        engine.add(instrument)
    index = engine.index
    assert isinstance(index, MemoryIndex)
    return index


@pytest.fixture
def index_path(tmp_path: Path) -> str:
    root = tmp_path / "bills"
    root.mkdir()
    for instrument in golden_instruments():
        key = instrument.key
        name = f"{key.jurisdiction}_{key.session}_{key.number}.txt"
        (root / name).write_text(instrument.raw_text, encoding="utf-8")
    path = tmp_path / "index.sqlite"
    run("index", "--index", str(path), str(root))
    return str(path)


def test_an_empty_index_measures_zero() -> None:
    measured = describe(MemoryIndex())
    assert measured.instruments == 0
    assert measured.mean_tokens == 0.0
    assert measured.selectivity == 0.0
    assert measured.widest_posting_share == 0.0


def test_the_corpus_is_counted() -> None:
    measured = describe(loaded())
    assert measured.instruments == 4
    assert measured.segments > 4
    assert set(measured.jurisdictions) == {"wy", "mt", "nd", "co"}
    assert set(measured.sessions) == {"2023", "2024", "2025"}


def test_averages_are_positive() -> None:
    measured = describe(loaded())
    assert measured.mean_tokens > 5
    assert measured.mean_fingerprints > 0


def test_selectivity_is_a_fraction() -> None:
    measured = describe(loaded())
    assert 0.0 < measured.selectivity <= 1.0
    assert measured.distinct_fingerprints <= measured.total_fingerprints


def test_the_widest_posting_is_reported() -> None:
    measured = describe(loaded())
    assert measured.widest_posting >= 1
    assert 0.0 < measured.widest_posting_share <= 1.0


def test_the_summary_lines_mention_everything() -> None:
    lines = "\n".join(describe(loaded()).lines())
    assert "instruments" in lines
    assert "jurisdictions:" in lines
    assert "selectivity" in lines
    assert "engine digest" in lines


def test_an_empty_summary_says_none() -> None:
    lines = "\n".join(CorpusStats(instruments=0, segments=0).lines())
    assert "jurisdictions: none" in lines


def test_the_command_prints_the_summary(index_path: str) -> None:
    code, output = run("stats", "--index", index_path)
    assert code == EXIT_OK
    assert "4 instruments" in output


def test_the_command_can_emit_json(index_path: str) -> None:
    _, output = run("stats", "--json", "--index", index_path)
    payload = json.loads(output)
    assert payload["instruments"] == 4
    assert payload["jurisdictions"]["wy"] == 1
    assert len(payload["engine_digest"]) == 16


def test_the_index_option_is_required() -> None:
    assert run("stats")[0] == EXIT_USAGE


def test_a_missing_index_is_an_error(tmp_path: Path) -> None:
    assert run("stats", "--index", str(tmp_path / "missing" / "i.sqlite"))[0] == EXIT_ERROR

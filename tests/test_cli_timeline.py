from __future__ import annotations

import io
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.diffusion_cmd import render_timeline
from lexdrift.cli.main import EXIT_FINDINGS, main
from lexdrift.diffusion.lineage import lineages
from lexdrift.pipeline import Engine


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
    for instrument in golden_instruments():
        key = instrument.key
        (root / f"{key.jurisdiction}_{key.session}_{key.number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    return str(root)


def state_family():
    result = Engine().run_over(golden_instruments())
    family = next(f for f in lineages(result.graph) if f.size == 3)
    return result.graph, family


def test_the_timeline_lists_every_member() -> None:
    graph, family = state_family()
    lines = render_timeline(graph, family)
    assert len(lines) == family.size + 1
    assert lines[0].strip() == "timeline:"


def test_the_first_member_is_the_origin() -> None:
    graph, family = state_family()
    assert "origin" in render_timeline(graph, family)[1]


def test_later_members_report_their_offset() -> None:
    graph, family = state_family()
    body = "\n".join(render_timeline(graph, family))
    assert "days later" in body
    assert "characters from" in body


def test_the_dates_run_forward() -> None:
    graph, family = state_family()
    dates = [line.split()[0] for line in render_timeline(graph, family)[1:]]
    assert dates == sorted(dates)


def test_the_command_prints_a_timeline(corpus: str) -> None:
    code, output = run("diffusion", "--timeline", corpus)
    assert code == EXIT_FINDINGS
    assert "timeline:" in output
    assert "origin" in output


def test_the_timeline_is_off_by_default(corpus: str) -> None:
    _, output = run("diffusion", corpus)
    assert "timeline:" not in output


def test_a_clean_corpus_reports_no_cycles(corpus: str) -> None:
    _, output = run("diffusion", corpus)
    assert "dates disagree" not in output

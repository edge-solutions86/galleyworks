from __future__ import annotations

import io
from pathlib import Path

import pytest
from pydantic import ValidationError

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_OK, main
from lexdrift.config import EngineConfig, ReportConfig
from lexdrift.match.explain import Confidence
from lexdrift.pipeline import Engine
from lexdrift.report.ranking import apply_report_config


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def report_for(config: EngineConfig | None = None, slug: str = "nd/2025/lower/HB0044"):
    result = Engine(config).run_over(golden_instruments())
    found = result.report_for(slug)
    assert found is not None
    return found


def test_the_defaults_show_everything() -> None:
    settings = ReportConfig()
    assert settings.min_confidence == "weak"
    assert not settings.crossing_only
    assert settings.top is None
    assert settings.max_passages == 3


def test_the_defaults_change_nothing() -> None:
    plain = report_for()
    assert apply_report_config(plain).matches == plain.matches


def test_a_confidence_floor_narrows_the_report() -> None:
    strict = EngineConfig(report=ReportConfig(min_confidence="strong"))
    assert all(m.confidence is Confidence.STRONG for m in report_for(strict).matches)


def test_a_top_limit_narrows_the_report() -> None:
    limited = EngineConfig(report=ReportConfig(top=1))
    assert len(report_for(limited).matches) <= 1


def test_crossing_only_drops_same_jurisdiction_findings() -> None:
    crossing = EngineConfig(report=ReportConfig(crossing_only=True))
    built = report_for(crossing)
    assert all(m.other_key.jurisdiction != built.key.jurisdiction for m in built.matches)


def test_the_summary_still_describes_the_whole_document() -> None:
    limited = EngineConfig(report=ReportConfig(top=1))
    assert report_for(limited).summary == report_for().summary


def test_a_zero_top_is_rejected() -> None:
    with pytest.raises(ValidationError):
        ReportConfig(top=0)


def test_the_report_config_is_not_part_of_the_digest() -> None:
    base = EngineConfig()
    narrowed = EngineConfig(report=ReportConfig(min_confidence="strong", top=1))
    assert base.digest() == narrowed.digest()


def test_the_config_command_reports_the_defaults() -> None:
    code, output = run("config")
    assert code == EXIT_OK
    assert "report: confidence >= weak" in output
    assert "top all" in output


def test_a_config_file_narrows_a_run(tmp_path: Path) -> None:
    root = tmp_path / "bills"
    root.mkdir()
    for instrument in golden_instruments():
        key = instrument.key
        (root / f"{key.jurisdiction}_{key.session}_{key.number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    settings = tmp_path / "engine.yaml"
    settings.write_text("report:\n  crossing_only: true\n  top: 1\n", encoding="utf-8")
    _, output = run("report", "--config", str(settings), "--only-findings", str(root))
    assert output.count(" matches ") <= 4

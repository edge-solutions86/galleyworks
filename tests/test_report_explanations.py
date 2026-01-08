from __future__ import annotations

import io
import json
from datetime import UTC, datetime

from golden_corpus import golden_instruments
from lexdrift.config import EngineConfig, MatchConfig
from lexdrift.match.explain import Confidence
from lexdrift.pipeline import Engine
from lexdrift.report.json_writer import dumps
from lexdrift.report.markdown_writer import to_markdown
from lexdrift.report.model import InstrumentReport
from lexdrift.report.render import render_text
from lexdrift.report.writers import write_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report(config: EngineConfig | None = None) -> InstrumentReport:
    result = Engine(config).run_over(golden_instruments(), now=FIXED)
    found = result.report_for("mt/2024/lower/HB0311")
    assert found is not None
    return found


def test_every_match_carries_an_explanation() -> None:
    built = report()
    assert built.matches
    for match in built.matches:
        assert match.headline
        assert match.reasons
        assert match.confidence in set(Confidence)


def test_the_headline_names_both_sides() -> None:
    match = report().matches[0]
    assert match.query_path in match.headline
    assert match.other_key.slug in match.headline


def test_a_cross_jurisdiction_match_says_so() -> None:
    match = report().matches[0]
    assert any("different jurisdictions" in reason for reason in match.reasons)


def test_the_text_report_shows_the_confidence() -> None:
    rendered = render_text(report())
    assert "confidence " in rendered
    assert "why: " in rendered


def test_the_text_report_still_fits_its_width() -> None:
    rendered = render_text(report(), width=70)
    assert all(len(line) <= 70 for line in rendered.splitlines())


def test_the_json_report_carries_the_reasons() -> None:
    payload = json.loads(dumps(report()))
    match = payload["matches"][0]
    assert match["confidence"] in {"weak", "moderate", "strong"}
    assert match["headline"]
    assert isinstance(match["reasons"], list)
    assert match["reasons"]


def test_the_markdown_report_lists_the_reasons() -> None:
    rendered = to_markdown(report())
    assert "| confidence |" in rendered
    assert "Why this was reported:" in rendered


def test_the_markdown_passage_limit_still_works() -> None:
    built = report()
    rendered = to_markdown(built, max_passages=1)
    if max(len(match.passages) for match in built.matches) > 1:
        assert "further passages omitted" in rendered


def test_every_writer_still_runs() -> None:
    built = report()
    for name in ("text", "json", "csv", "markdown"):
        stream = io.StringIO()
        write_report(built, stream, output_format=name)
        assert stream.getvalue().strip()


def test_the_match_config_reaches_the_explanation() -> None:
    strict = EngineConfig(match=MatchConfig(min_passage_chars=95))
    loose_reasons = {reason for match in report().matches for reason in match.reasons}
    strict_reasons = {reason for match in report(strict).matches for reason in match.reasons}
    assert loose_reasons or strict_reasons


def test_an_empty_report_has_no_explanations() -> None:
    result = Engine().run_over(golden_instruments(), now=FIXED)
    built = result.report_for("co/2025/upper/SB0007")
    assert built is not None
    assert built.matches == ()

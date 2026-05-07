from __future__ import annotations

from datetime import UTC, datetime

from golden_corpus import golden_instruments
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.match.explain import Confidence
from lexdrift.pipeline import Engine
from lexdrift.report.markdown_writer import to_markdown
from lexdrift.report.model import ReportedMatch, ReportedPassage
from lexdrift.report.render import render_text
from lexdrift.report.shared import ELLIPSIS, flow, quote, span_payload, trim_passages
from lexdrift.util.spans import TextSpan

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def passage(text: str = "words") -> ReportedPassage:
    return ReportedPassage(
        query_span=TextSpan(0, 10),
        other_span=TextSpan(0, 10),
        query_text=text,
        other_text=text,
        resolved=True,
    )


def match(count: int) -> ReportedMatch:
    return ReportedMatch(
        query_path="1(a)",
        other_key=InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"),
        other_path="1(a)",
        containment=0.5,
        jaccard=0.5,
        passages=tuple(passage(f"passage {n}") for n in range(count)),
        confidence=Confidence.MODERATE,
        headline="h",
        reasons=("r",),
    )


def test_flow_collapses_every_kind_of_whitespace() -> None:
    assert flow("  the\n board\tshall   act  ") == "the board shall act"


def test_flow_of_nothing_is_empty() -> None:
    assert flow("   \n\t ") == ""


def test_quote_returns_the_fallback_for_empty_text() -> None:
    assert quote("", empty="(none)") == "(none)"
    assert quote("   ", empty="(none)") == "(none)"


def test_quote_leaves_short_text_alone() -> None:
    assert quote("the board", limit=100) == "the board"


def test_quote_truncates_with_an_ellipsis() -> None:
    truncated = quote("x" * 100, limit=10)
    assert len(truncated) == 10
    assert truncated.endswith(ELLIPSIS)


def test_quote_without_a_limit_never_truncates() -> None:
    assert quote("x" * 1000) == "x" * 1000


def test_span_payload_is_self_consistent() -> None:
    payload = span_payload(TextSpan(12, 40))
    assert payload == {"start": 12, "end": 40, "length": 28}


def test_trim_passages_keeps_everything_under_the_limit() -> None:
    trimmed, hidden = trim_passages(match(2), 5)
    assert hidden == 0
    assert len(trimmed.passages) == 2


def test_trim_passages_reports_what_it_hid() -> None:
    trimmed, hidden = trim_passages(match(7), 3)
    assert hidden == 4
    assert len(trimmed.passages) == 3


def test_trim_passages_with_no_limit() -> None:
    trimmed, hidden = trim_passages(match(7), None)
    assert hidden == 0
    assert len(trimmed.passages) == 7


def test_both_writers_quote_a_passage_identically() -> None:
    result = Engine().run_over(golden_instruments(), now=FIXED)
    report = result.report_for("mt/2024/lower/HB0311")
    assert report is not None
    first = report.matches[0].passages[0].query_text
    expected = flow(first)
    assert expected in to_markdown(report)
    rendered = render_text(report, width=10_000)
    assert expected[:60] in rendered

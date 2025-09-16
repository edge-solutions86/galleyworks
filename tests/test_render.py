from __future__ import annotations

from datetime import UTC, datetime

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report
from lexdrift.report.render import render_text

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report(resolved: bool = True) -> InstrumentReport:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    matches = Matcher(index).match_instrument(probe)
    source = MappingTextSource([source_bill]) if resolved else MappingTextSource()
    return build_report(probe, matches, source, now=FIXED)


def empty() -> InstrumentReport:
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    return build_report(probe, [], now=FIXED)


def test_the_header_names_the_instrument() -> None:
    rendered = render_text(report())
    assert rendered.startswith("lexdrift report for mt/2025/lower/HB0311")


def test_the_summary_line_is_present() -> None:
    rendered = render_text(report())
    assert "segments matched" in rendered
    assert "characters reused" in rendered


def test_the_timestamp_is_rendered_to_the_second() -> None:
    assert "generated 2026-01-02T03:04:05+00:00" in render_text(report())


def test_findings_name_both_sides() -> None:
    rendered = render_text(report())
    assert "matches wy/2025/lower/HB0142 at" in rendered


def test_scores_are_shown() -> None:
    rendered = render_text(report())
    assert "containment " in rendered
    assert "jaccard " in rendered


def test_passages_are_quoted_from_both_documents() -> None:
    rendered = render_text(report())
    assert "query:" in rendered
    assert "other:" in rendered


def test_an_unresolved_passage_says_so() -> None:
    rendered = render_text(report(resolved=False))
    assert "other: (not available)" in rendered


def test_quotations_are_wrapped_to_the_width() -> None:
    rendered = render_text(report(), width=70)
    assert all(len(line) <= 70 for line in rendered.splitlines())


def test_an_empty_report_says_nothing_was_found() -> None:
    rendered = render_text(empty())
    assert "no reuse found" in rendered
    assert "matches" not in rendered.split("no reuse found")[1]


def test_the_match_limit_is_reported() -> None:
    built = report()
    rendered = render_text(built, max_matches=1)
    if len(built.matches) > 1:
        assert "further matches" in rendered
    assert rendered.count(" matches ") <= 2


def test_the_passage_limit_is_reported() -> None:
    built = report()
    rendered = render_text(built, max_passages=1)
    total = max(len(match.passages) for match in built.matches)
    if total > 1:
        assert "further passages" in rendered


def test_the_output_ends_with_one_newline() -> None:
    rendered = render_text(report())
    assert rendered.endswith("\n")
    assert not rendered.endswith("\n\n")


def test_no_line_contains_a_raw_newline_from_the_source() -> None:
    rendered = render_text(report())
    quoted = [line for line in rendered.splitlines() if line.strip().startswith("query:")]
    assert quoted
    assert all("\n" not in line for line in quoted)

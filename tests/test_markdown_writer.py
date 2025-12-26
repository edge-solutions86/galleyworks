from __future__ import annotations

import io
from datetime import UTC, datetime

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.markdown_writer import to_markdown, write_markdown
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report() -> InstrumentReport:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    matches = Matcher(index).match_instrument(probe)
    return build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)


def empty() -> InstrumentReport:
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    return build_report(probe, [], now=FIXED)


def test_the_heading_names_the_instrument() -> None:
    assert to_markdown(report()).startswith("# Reuse report: mt/2025/lower/HB0311")


def test_the_timestamp_is_present() -> None:
    assert "Generated 2026-01-02T03:04:05+00:00." in to_markdown(report())


def test_the_summary_is_a_table() -> None:
    rendered = to_markdown(report())
    assert "## Summary" in rendered
    assert "| segments matched |" in rendered
    assert "| reuse ratio |" in rendered


def test_findings_are_numbered() -> None:
    rendered = to_markdown(report())
    assert "## Findings" in rendered
    assert "### 1. " in rendered


def test_a_finding_names_both_sides() -> None:
    assert "into wy/2025/lower/HB0142" in to_markdown(report())


def test_passages_are_fenced() -> None:
    rendered = to_markdown(report())
    assert rendered.count("```") % 2 == 0
    assert rendered.count("```") >= 4


def test_quotations_are_one_line_inside_the_fence() -> None:
    rendered = to_markdown(report())
    inside = rendered.split("```")[1].strip()
    assert "\n" not in inside


def test_the_passage_limit_is_reported() -> None:
    built = report()
    rendered = to_markdown(built, max_passages=1)
    if max(len(match.passages) for match in built.matches) > 1:
        assert "further passages omitted" in rendered


def test_pipes_in_a_cell_are_escaped() -> None:
    rendered = to_markdown(report())
    for line in rendered.splitlines():
        if line.startswith("| other jurisdiction |"):
            assert line.count("|") == 3


def test_an_empty_report_says_so() -> None:
    rendered = to_markdown(empty())
    assert "No reuse found." in rendered
    assert "## Findings" not in rendered


def test_the_document_ends_with_one_newline() -> None:
    rendered = to_markdown(report())
    assert rendered.endswith("\n")
    assert not rendered.endswith("\n\n")


def test_write_markdown_sends_it_to_a_stream() -> None:
    stream = io.StringIO()
    write_markdown(report(), stream)
    assert stream.getvalue() == to_markdown(report())

"""The output formats are a contract, so they are pinned.

Every one of these has a consumer that breaks silently when the shape moves: a
spreadsheet with a column order, a script reading a JSON key, a ticket template
expecting a heading. Renaming a field is allowed; doing it without noticing is
not, and that is what these tests are for.

They assert structure and stable keys rather than whole documents. A golden file
of the entire rendered report would fail on every wording change and be updated
without being read, which is worse than no test.
"""

from __future__ import annotations

import csv
import io
import json
from datetime import UTC, datetime

import pytest

from golden_corpus import golden_instruments
from lexdrift.pipeline import Engine
from lexdrift.report.csv_writer import COLUMNS
from lexdrift.report.json_writer import FORMAT_VERSION, to_mapping
from lexdrift.report.model import InstrumentReport
from lexdrift.report.writers import format_names, write_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)

TOP_LEVEL_KEYS = {"format", "instrument", "generated_at", "summary", "matches"}
SUMMARY_KEYS = {
    "matched_segments",
    "total_segments",
    "reused_characters",
    "document_characters",
    "other_instruments",
    "reuse_ratio",
}
MATCH_KEYS = {
    "query_path",
    "other",
    "containment",
    "jaccard",
    "characters",
    "resolved",
    "confidence",
    "headline",
    "reasons",
    "passages",
}
PASSAGE_KEYS = {"query", "other", "characters", "resolved", "query_text", "other_text"}


@pytest.fixture(scope="module")
def report() -> InstrumentReport:
    result = Engine().run_over(golden_instruments(), now=FIXED)
    found = result.report_for("nd/2025/lower/HB0044")
    assert found is not None
    assert found.matches
    return found


def rendered(report: InstrumentReport, name: str) -> str:
    stream = io.StringIO()
    write_report(report, stream, output_format=name)
    return stream.getvalue()


def test_the_json_format_version_is_pinned(report: InstrumentReport) -> None:
    assert to_mapping(report)["format"] == FORMAT_VERSION == 1


def test_the_json_top_level_keys_are_pinned(report: InstrumentReport) -> None:
    assert set(to_mapping(report)) == TOP_LEVEL_KEYS


def test_the_json_summary_keys_are_pinned(report: InstrumentReport) -> None:
    assert set(to_mapping(report)["summary"]) == SUMMARY_KEYS


def test_the_json_match_keys_are_pinned(report: InstrumentReport) -> None:
    assert set(to_mapping(report)["matches"][0]) == MATCH_KEYS


def test_the_json_passage_keys_are_pinned(report: InstrumentReport) -> None:
    assert set(to_mapping(report)["matches"][0]["passages"][0]) == PASSAGE_KEYS


def test_the_json_instrument_block_is_pinned(report: InstrumentReport) -> None:
    assert set(to_mapping(report)["instrument"]) == {
        "key",
        "jurisdiction",
        "session",
        "chamber",
        "number",
        "title",
    }


def test_the_csv_column_order_is_pinned() -> None:
    assert COLUMNS[:4] == (
        "query_instrument",
        "query_jurisdiction",
        "query_path",
        "other_instrument",
    )
    assert COLUMNS[-1] == "query_text"
    assert len(COLUMNS) == 15


def test_the_csv_header_matches_the_columns(report: InstrumentReport) -> None:
    reader = csv.reader(io.StringIO(rendered(report, "csv")))
    assert tuple(next(reader)) == COLUMNS


def test_the_text_report_opens_with_its_heading(report: InstrumentReport) -> None:
    assert rendered(report, "text").splitlines()[0].startswith("lexdrift report for ")


def test_the_markdown_report_opens_with_its_heading(report: InstrumentReport) -> None:
    assert rendered(report, "markdown").splitlines()[0].startswith("# Reuse report: ")


@pytest.mark.parametrize("name", ["text", "json", "csv", "markdown"])
def test_every_format_ends_with_a_single_newline(report: InstrumentReport, name: str) -> None:
    output = rendered(report, name)
    assert output.endswith("\n")
    assert not output.endswith("\n\n")


@pytest.mark.parametrize("name", ["text", "json", "csv", "markdown"])
def test_every_format_is_deterministic(report: InstrumentReport, name: str) -> None:
    assert rendered(report, name) == rendered(report, name)


def test_the_format_list_is_pinned() -> None:
    assert format_names() == ["text", "json", "csv", "markdown"]


def test_the_json_document_parses_and_round_trips(report: InstrumentReport) -> None:
    payload = json.loads(rendered(report, "json"))
    assert payload == to_mapping(report)

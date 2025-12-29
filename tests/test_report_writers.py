from __future__ import annotations

import io
from datetime import UTC, datetime

import pytest

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.errors import ConfigError
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report
from lexdrift.report.writers import FORMATS, format_names, suffix_for, write_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report() -> InstrumentReport:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    matches = Matcher(index).match_instrument(probe)
    return build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)


def rendered(output_format: str) -> str:
    stream = io.StringIO()
    write_report(report(), stream, output_format=output_format)
    return stream.getvalue()


def test_every_format_is_registered() -> None:
    assert format_names() == ["text", "json", "csv", "markdown"]


def test_every_writer_describes_itself() -> None:
    assert all(writer.description for writer in FORMATS.values())
    assert all(writer.suffix for writer in FORMATS.values())


def test_suffixes_are_distinct() -> None:
    suffixes = [writer.suffix for writer in FORMATS.values()]
    assert len(suffixes) == len(set(suffixes))


@pytest.mark.parametrize("name", ["text", "json", "csv", "markdown"])
def test_every_format_writes_something(name: str) -> None:
    assert rendered(name).strip()


def test_text_is_the_default() -> None:
    stream = io.StringIO()
    write_report(report(), stream)
    assert stream.getvalue().startswith("lexdrift report for")


def test_json_is_json() -> None:
    assert rendered("json").lstrip().startswith("{")


def test_csv_starts_with_its_header() -> None:
    assert rendered("csv").startswith("query_instrument,")


def test_markdown_starts_with_a_heading() -> None:
    assert rendered("markdown").startswith("# Reuse report:")


def test_the_width_reaches_the_text_writer() -> None:
    stream = io.StringIO()
    write_report(report(), stream, output_format="text", width=60)
    assert all(len(line) <= 60 for line in stream.getvalue().splitlines())


def test_suffix_lookup() -> None:
    assert suffix_for("markdown") == "md"
    assert suffix_for("text") == "txt"


def test_an_unknown_format_names_the_known_ones() -> None:
    with pytest.raises(ConfigError, match="unknown output format") as caught:
        write_report(report(), io.StringIO(), output_format="yaml")
    assert "markdown" in caught.value.context["known"]


def test_an_unknown_suffix_is_rejected() -> None:
    with pytest.raises(ConfigError, match="unknown output format"):
        suffix_for("yaml")

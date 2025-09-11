from __future__ import annotations

import io
import json
from datetime import UTC, datetime

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.json_writer import FORMAT_VERSION, dumps, to_mapping, write_json
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report() -> InstrumentReport:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    matches = Matcher(index).match_instrument(probe)
    return build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)


def empty_report() -> InstrumentReport:
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    return build_report(probe, [], now=FIXED)


def test_the_payload_declares_its_format() -> None:
    assert to_mapping(report())["format"] == FORMAT_VERSION


def test_the_instrument_block_is_split_out() -> None:
    payload = to_mapping(report())["instrument"]
    assert payload["key"] == "mt/2025/lower/HB0311"
    assert payload["jurisdiction"] == "mt"
    assert payload["chamber"] == "lower"
    assert payload["title"].startswith("AN ACT")


def test_the_timestamp_is_iso() -> None:
    assert to_mapping(report())["generated_at"] == "2026-01-02T03:04:05+00:00"


def test_the_summary_is_present_and_rounded() -> None:
    summary = to_mapping(report())["summary"]
    assert summary["matched_segments"] >= 1
    assert 0.0 < summary["reuse_ratio"] <= 1.0
    assert isinstance(summary["reused_characters"], int)


def test_spans_carry_their_length() -> None:
    passage = to_mapping(report())["matches"][0]["passages"][0]
    span = passage["query"]
    assert span["length"] == span["end"] - span["start"]


def test_text_is_included_by_default() -> None:
    passage = to_mapping(report())["matches"][0]["passages"][0]
    assert passage["query_text"]
    assert passage["other_text"]


def test_text_can_be_left_out() -> None:
    passage = to_mapping(report(), include_text=False)["matches"][0]["passages"][0]
    assert "query_text" not in passage
    assert passage["characters"] > 0


def test_the_other_instrument_is_fully_named() -> None:
    other = to_mapping(report())["matches"][0]["other"]
    assert other["instrument"] == "wy/2025/lower/HB0142"
    assert other["jurisdiction"] == "wy"
    assert other["number"] == "HB0142"


def test_dumps_produces_parseable_json() -> None:
    payload = json.loads(dumps(report()))
    assert payload["instrument"]["key"] == "mt/2025/lower/HB0311"


def test_dumps_can_be_compact() -> None:
    compact = dumps(report(), indent=None)
    assert "\n" not in compact.strip()


def test_write_json_terminates_with_a_newline() -> None:
    stream = io.StringIO()
    write_json(report(), stream)
    written = stream.getvalue()
    assert written.endswith("\n")
    assert json.loads(written)["format"] == FORMAT_VERSION


def test_an_empty_report_still_serializes() -> None:
    payload = json.loads(dumps(empty_report()))
    assert payload["matches"] == []
    assert payload["summary"]["reuse_ratio"] == 0.0


def test_non_ascii_survives() -> None:
    probe = make_bill(jurisdiction="pr", number="PC0001", body="El consejo publicará el padrón.")
    payload = json.loads(dumps(build_report(probe, [], now=FIXED)))
    assert payload["instrument"]["jurisdiction"] == "pr"

from __future__ import annotations

from datetime import UTC, datetime

import pytest
from pydantic import ValidationError

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.model import InstrumentReport, MappingTextSource, build_report
from lexdrift.service.schemas import HealthResponse, QueryRequest, ReportOut

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def report() -> InstrumentReport:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    matches = Matcher(index).match_instrument(probe)
    return build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)


def test_a_query_needs_text_and_a_jurisdiction() -> None:
    request = QueryRequest(text="AN ACT", jurisdiction="wy")
    assert request.session is None
    assert request.include_text


def test_an_empty_query_is_rejected() -> None:
    with pytest.raises(ValidationError):
        QueryRequest(text="", jurisdiction="wy")


def test_unknown_fields_are_rejected() -> None:
    with pytest.raises(ValidationError):
        QueryRequest(text="x", jurisdiction="wy", extra=1)  # type: ignore[call-arg]


def test_requests_are_frozen() -> None:
    request = QueryRequest(text="x", jurisdiction="wy")
    with pytest.raises(ValidationError):
        request.jurisdiction = "mt"  # type: ignore[misc]


def test_a_report_maps_onto_the_wire_model() -> None:
    payload = ReportOut.of(report(), digest="abc123")
    assert payload.instrument == "mt/2025/lower/HB0311"
    assert payload.jurisdiction == "mt"
    assert payload.engine_digest == "abc123"
    assert payload.generated_at == FIXED
    assert payload.matches


def test_the_summary_is_carried_across() -> None:
    payload = ReportOut.of(report(), digest="abc123")
    assert payload.summary.matched_segments >= 1
    assert 0.0 < payload.summary.reuse_ratio <= 1.0


def test_matches_name_the_other_instrument() -> None:
    match = ReportOut.of(report(), digest="abc123").matches[0]
    assert match.other_instrument == "wy/2025/lower/HB0142"
    assert match.other_jurisdiction == "wy"
    assert match.characters > 0


def test_passages_carry_spans_and_text() -> None:
    passage = ReportOut.of(report(), digest="abc123").matches[0].passages[0]
    assert passage.query_end > passage.query_start
    assert passage.query_text
    assert passage.resolved


def test_text_can_be_left_out() -> None:
    payload = ReportOut.of(report(), digest="abc123", include_text=False)
    passage = payload.matches[0].passages[0]
    assert passage.query_text is None
    assert passage.characters > 0


def test_scores_are_rounded() -> None:
    match = ReportOut.of(report(), digest="abc123").matches[0]
    assert len(str(match.containment).split(".")[-1]) <= 6


def test_an_empty_report_maps_cleanly() -> None:
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    payload = ReportOut.of(build_report(probe, [], now=FIXED), digest="abc123")
    assert payload.matches == ()
    assert payload.summary.reuse_ratio == 0.0


def test_the_health_model_round_trips() -> None:
    health = HealthResponse(
        status="ok", version="0.1.0", engine_digest="abc123", instruments=2, segments=9
    )
    assert health.model_dump()["status"] == "ok"
    assert health.segments == 9

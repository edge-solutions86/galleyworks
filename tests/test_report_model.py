from __future__ import annotations

from datetime import UTC, datetime

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher
from lexdrift.report.model import (
    MappingTextSource,
    TextSource,
    build_report,
    group_by_instrument,
)

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def pair() -> tuple:
    source_bill = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(source_bill))
    return source_bill, probe, Matcher(index).match_instrument(probe)


def test_a_mapping_source_satisfies_the_protocol() -> None:
    assert isinstance(MappingTextSource(), TextSource)


def test_a_mapping_source_finds_what_it_holds() -> None:
    source_bill, _, _ = pair()
    source = MappingTextSource([source_bill])
    assert source.text_of(source_bill.key) == source_bill.raw_text
    assert source.text_of(make_bill(jurisdiction="nd", number="HB1", body="x").key) is None


def test_a_report_quotes_both_sides() -> None:
    source_bill, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)
    assert not report.is_empty
    passage = report.matches[0].passages[0]
    assert passage.resolved
    assert passage.query_text
    assert passage.other_text
    assert passage.characters > 0


def test_an_unresolved_other_side_is_flagged() -> None:
    _, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource(), now=FIXED)
    assert not report.matches[0].resolved
    assert report.matches[0].passages[0].other_text == ""
    assert report.matches[0].passages[0].query_text


def test_the_report_carries_its_identity() -> None:
    source_bill, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)
    assert report.key == probe.key
    assert report.title.startswith("AN ACT")
    assert report.generated_at == FIXED


def test_the_summary_counts_segments_and_characters() -> None:
    source_bill, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)
    summary = report.summary
    assert summary.matched_segments >= 1
    assert summary.total_segments >= summary.matched_segments
    assert summary.reused_characters > 0
    assert summary.other_instruments == 1
    assert 0.0 < summary.reuse_ratio <= 1.0


def test_overlapping_passages_are_counted_once() -> None:
    source_bill, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)
    naive = sum(passage.characters for match in report.matches for passage in match.passages)
    assert report.summary.reused_characters <= naive


def test_an_empty_report() -> None:
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    report = build_report(probe, [], now=FIXED)
    assert report.is_empty
    assert report.summary.reused_characters == 0
    assert report.summary.reuse_ratio == 0.0
    assert report.summary.other_instruments == 0


def test_scores_are_carried_through() -> None:
    source_bill, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)
    reported = report.matches[0]
    assert 0.0 < reported.containment <= 1.0
    assert 0.0 < reported.jaccard <= 1.0
    assert reported.characters > 0


def test_matches_group_by_the_other_instrument() -> None:
    source_bill, probe, matches = pair()
    report = build_report(probe, matches, MappingTextSource([source_bill]), now=FIXED)
    grouped = group_by_instrument(report)
    assert list(grouped) == [source_bill.key.slug]
    assert len(grouped[source_bill.key.slug]) == len(report.matches)


def test_a_source_can_be_extended_after_construction() -> None:
    source_bill, probe, matches = pair()
    source = MappingTextSource()
    source.add(source_bill)
    report = build_report(probe, matches, source, now=FIXED)
    assert report.matches[0].resolved

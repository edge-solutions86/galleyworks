from __future__ import annotations

from datetime import UTC, date, datetime

from golden_corpus import golden_instruments
from lexdrift.diffusion.lineage import lineages
from lexdrift.diffusion.metrics import measure
from lexdrift.pipeline import Engine, RunResult

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def run() -> RunResult:
    return Engine().run_over(golden_instruments(), now=FIXED)


def test_the_corpus_parses() -> None:
    instruments = golden_instruments()
    assert [instrument.key.slug for instrument in instruments] == [
        "wy/2023/lower/HB0142",
        "mt/2024/lower/HB0311",
        "nd/2025/lower/HB0044",
        "co/2025/upper/SB0007",
    ]


def test_effective_dates_are_read() -> None:
    dates = [instrument.effective_date for instrument in golden_instruments()]
    assert dates == [
        date(2023, 7, 1),
        date(2024, 10, 1),
        date(2025, 8, 1),
        date(2025, 9, 1),
    ]


def test_the_wyoming_bill_has_the_expected_structure() -> None:
    wyoming = golden_instruments()[0]
    paths = [segment.path.printed for segment in wyoming.body_segments()]
    assert paths == ["1", "1(a)", "1(b)", "1(b)(i)", "1(b)(ii)", "2"]


def test_montana_reuses_wyoming() -> None:
    result = run()
    report = result.report_for("mt/2024/lower/HB0311")
    assert report is not None
    assert not report.is_empty
    assert {match.other_key.jurisdiction for match in report.matches} == {"wy"}


def test_north_dakota_reuses_the_earlier_two() -> None:
    result = run()
    report = result.report_for("nd/2025/lower/HB0044")
    assert report is not None
    assert {match.other_key.jurisdiction for match in report.matches} == {"wy", "mt"}


def test_colorado_reuses_nothing() -> None:
    result = run()
    report = result.report_for("co/2025/upper/SB0007")
    assert report is not None
    assert report.is_empty


def test_the_first_instrument_has_nothing_to_match() -> None:
    assert run().reports[0].is_empty


def test_there_is_one_family_of_three() -> None:
    families = [family for family in lineages(run().graph) if family.size > 1]
    assert len(families) == 1
    assert families[0].size == 3


def test_wyoming_is_the_root() -> None:
    result = run()
    family = next(family for family in lineages(result.graph) if family.size > 1)
    assert family.root is not None
    assert family.root.jurisdiction == "wy"
    assert family.confident


def test_the_family_spans_three_jurisdictions_and_two_years() -> None:
    result = run()
    family = next(family for family in lineages(result.graph) if family.size > 1)
    metrics = measure(result.graph, family)
    assert metrics.jurisdictions == 3
    assert metrics.span_days == 762
    assert metrics.characters > 500


def test_colorado_is_not_in_the_family() -> None:
    result = run()
    family = next(family for family in lineages(result.graph) if family.size > 1)
    assert all(member.jurisdiction != "co" for member in family.members)


def test_the_dropped_subsection_is_not_reported_as_reused() -> None:
    result = run()
    report = result.report_for("nd/2025/lower/HB0044")
    assert report is not None
    quoted = " ".join(passage.query_text for match in report.matches for passage in match.passages)
    assert "certified copy" not in quoted


def test_the_run_is_reproducible() -> None:
    first = run()
    second = run()
    assert [report.summary.reused_characters for report in first.reports] == [
        report.summary.reused_characters for report in second.reports
    ]
    assert [edge.pair for edge in first.graph] == [edge.pair for edge in second.graph]

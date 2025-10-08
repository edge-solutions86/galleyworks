from __future__ import annotations

from datetime import UTC, date, datetime
from pathlib import Path

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, make_bill
from lexdrift.corpus import discover
from lexdrift.pipeline import Engine

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


def corpus_dir(tmp_path: Path) -> Path:
    earlier = make_bill(
        jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE, enacted=date(2024, 7, 1)
    )
    later = make_bill(
        jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE, enacted=date(2025, 7, 1)
    )
    other = make_bill(
        jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE, enacted=date(2025, 8, 1)
    )
    for instrument in (earlier, later, other):
        name = f"{instrument.key.jurisdiction}_2025_{instrument.key.number}.txt"
        (tmp_path / name).write_text(instrument.raw_text, encoding="utf-8")
    return tmp_path


def test_a_run_indexes_and_matches_in_order(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    assert result.instrument_count == 3
    assert result.indexed_segments > 0
    assert result.match_count > 0


def test_the_first_instrument_matches_nothing(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    first = result.reports[0]
    assert first.is_empty


def test_a_later_copy_is_reported(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    report = result.report_for("wy/2025/lower/HB0142")
    assert report is not None
    assert not report.is_empty
    assert report.matches[0].other_key.jurisdiction == "mt"


def test_reports_quote_the_other_document(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    quoted = [
        passage
        for report in result.reports
        for match in report.matches
        for passage in match.passages
    ]
    assert quoted
    assert all(passage.resolved for passage in quoted)


def test_no_instrument_matches_itself(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    for report in result.reports:
        assert all(match.other_key != report.key for match in report.matches)


def test_the_graph_is_dated_and_directed(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    assert len(result.graph) >= 1
    edge = result.graph.edges()[0]
    assert edge.directed
    assert result.graph.date_of(edge.source) is not None


def test_an_unrelated_bill_produces_an_empty_report(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    report = result.report_for("co/2025/lower/SB0007")
    assert report is not None
    assert report.is_empty


def test_report_for_an_unknown_slug_is_none(tmp_path: Path) -> None:
    result = Engine().run(discover(corpus_dir(tmp_path)), now=FIXED)
    assert result.report_for("nd/2025/lower/HB0001") is None


def test_boilerplate_can_be_learned_after_indexing(tmp_path: Path) -> None:
    engine = Engine()
    engine.run(discover(corpus_dir(tmp_path)), now=FIXED)
    learned = engine.learn_boilerplate(min_segments=2)
    assert learned >= 0
    assert engine.matcher().boilerplate is engine.boilerplate


def test_adding_one_instrument_at_a_time() -> None:
    engine = Engine()
    earlier = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    later = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert engine.add(earlier) > 0
    report, matches = engine.analyse(later, now=FIXED)
    assert matches
    assert not report.is_empty


def test_run_over_a_prepared_sequence() -> None:
    engine = Engine()
    instruments = [
        make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE),
        make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE),
    ]
    result = engine.run_over(instruments, now=FIXED)
    assert result.instrument_count == 2
    assert result.match_count > 0


def test_an_empty_corpus_runs_clean(tmp_path: Path) -> None:
    result = Engine().run(discover(tmp_path), now=FIXED)
    assert result.instrument_count == 0
    assert result.match_count == 0
    assert len(result.graph) == 0

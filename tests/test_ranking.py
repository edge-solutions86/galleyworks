from __future__ import annotations

import io
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.cli import build_cli
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, main
from lexdrift.errors import ConfigError
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.match.explain import Confidence
from lexdrift.pipeline import Engine
from lexdrift.report.model import InstrumentReport, ReportedMatch, ReportSummary
from lexdrift.report.ranking import filter_report, rank, rank_report

FIXED = datetime(2026, 1, 2, 3, 4, 5, tzinfo=UTC)


@pytest.fixture(autouse=True)
def _loaded() -> None:
    build_cli()


def run(*argv: str) -> tuple[int, str]:
    stream = io.StringIO()
    code = main(list(argv), stream)
    return code, stream.getvalue()


def match(confidence: Confidence, jurisdiction: str = "wy", path: str = "1") -> ReportedMatch:
    return ReportedMatch(
        query_path="1(a)",
        other_key=InstrumentKey(jurisdiction, "2025", Chamber.LOWER, "HB0142"),
        other_path=path,
        containment=0.5,
        jaccard=0.5,
        passages=(),
        confidence=confidence,
        headline="h",
        reasons=("r",),
    )


def synthetic(*matches: ReportedMatch) -> InstrumentReport:
    return InstrumentReport(
        key=InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311"),
        title="t",
        generated_at=FIXED,
        summary=ReportSummary(1, 2, 100, 1000, 1),
        matches=matches,
    )


def test_strong_findings_come_first() -> None:
    ordered = rank([match(Confidence.WEAK), match(Confidence.STRONG, path="2")])
    assert [item.confidence for item in ordered] == [Confidence.STRONG, Confidence.WEAK]


def test_the_order_is_stable() -> None:
    matches = [match(Confidence.MODERATE, path=str(n)) for n in range(5)]
    assert rank(matches) == rank(list(reversed(matches)))


def test_rank_report_reorders_in_place() -> None:
    report = synthetic(match(Confidence.WEAK), match(Confidence.STRONG, path="2"))
    assert rank_report(report).matches[0].confidence is Confidence.STRONG
    assert rank_report(report).summary == report.summary


def test_a_confidence_floor_drops_the_weak_ones() -> None:
    report = synthetic(match(Confidence.WEAK), match(Confidence.STRONG, path="2"))
    narrowed = filter_report(report, min_confidence=Confidence.STRONG)
    assert len(narrowed.matches) == 1
    assert narrowed.matches[0].confidence is Confidence.STRONG


def test_moderate_admits_strong() -> None:
    report = synthetic(match(Confidence.STRONG), match(Confidence.MODERATE, path="2"))
    assert len(filter_report(report, min_confidence=Confidence.MODERATE).matches) == 2


def test_crossing_only_drops_same_jurisdiction_findings() -> None:
    report = synthetic(match(Confidence.STRONG, jurisdiction="mt"), match(Confidence.STRONG))
    narrowed = filter_report(report, crossing_only=True)
    assert [item.other_key.jurisdiction for item in narrowed.matches] == ["wy"]


def test_a_limit_truncates() -> None:
    report = synthetic(*[match(Confidence.STRONG, path=str(n)) for n in range(6)])
    assert len(filter_report(report, limit=2).matches) == 2


def test_the_summary_is_never_recomputed() -> None:
    report = synthetic(match(Confidence.WEAK))
    assert filter_report(report, min_confidence=Confidence.STRONG).summary == report.summary


def test_a_negative_limit_is_rejected() -> None:
    with pytest.raises(ConfigError, match="cannot be negative"):
        filter_report(synthetic(), limit=-1)


def test_filtering_an_empty_report() -> None:
    assert filter_report(synthetic()).matches == ()


def test_a_replaced_report_keeps_its_identity() -> None:
    report = synthetic(match(Confidence.STRONG))
    assert replace(report, matches=()).key == report.key


@pytest.fixture
def corpus(tmp_path: Path) -> Path:
    root = tmp_path / "bills"
    root.mkdir()
    for instrument in golden_instruments():
        key = instrument.key
        (root / f"{key.jurisdiction}_{key.session}_{key.number}.txt").write_text(
            instrument.raw_text, encoding="utf-8"
        )
    return root


def test_the_report_command_takes_a_confidence_floor(corpus: Path) -> None:
    code, output = run("report", "--min-confidence", "strong", str(corpus))
    assert code in {EXIT_OK, EXIT_FINDINGS}
    assert "weak" not in output


def test_the_report_command_takes_a_top_limit(corpus: Path) -> None:
    _, output = run("report", "--top", "1", "--only-findings", str(corpus))
    assert output.count(" matches ") <= 4


def test_the_query_command_takes_the_same_flags(corpus: Path, tmp_path: Path) -> None:
    index_path = tmp_path / "index.sqlite"
    run("index", "--index", str(index_path), str(corpus))
    document = corpus / "mt_2024_HB0311.txt"
    code, output = run(
        "query",
        "--index",
        str(index_path),
        "--jurisdiction",
        "mt",
        "--crossing-only",
        "--top",
        "1",
        str(document),
    )
    assert code == EXIT_OK
    assert "lexdrift report for" in output


def test_the_engine_reports_are_ranked_by_default() -> None:
    result = Engine().run_over(golden_instruments(), now=FIXED)
    for report in result.reports:
        ranked = rank_report(report)
        assert [m.other_path for m in ranked.matches] == [
            m.other_path for m in rank(report.matches)
        ]

"""Ordering and filtering findings before they are printed.

A report with forty findings is not read; the first five are. Which five appear
first is therefore a decision about what the tool is for, and it is made here
rather than by whatever order the matcher happened to produce.

The order is confidence, then reused characters, then the slug. Confidence first
because a strong short finding is worth more attention than a weak long one, and
the slug last so that two runs over the same corpus print the same document.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import replace

from lexdrift.config import ReportConfig
from lexdrift.errors import ConfigError
from lexdrift.match.explain import Confidence
from lexdrift.report.model import InstrumentReport, ReportedMatch

__all__ = [
    "CONFIDENCE_ORDER",
    "apply_report_config",
    "filter_report",
    "rank",
    "rank_report",
]

CONFIDENCE_ORDER: dict[Confidence, int] = {
    Confidence.STRONG: 0,
    Confidence.MODERATE: 1,
    Confidence.WEAK: 2,
}


def _key(match: ReportedMatch) -> tuple[int, int, str]:
    return (
        CONFIDENCE_ORDER[match.confidence],
        -match.characters,
        f"{match.other_key.slug}#{match.other_path}",
    )


def rank(matches: Iterable[ReportedMatch]) -> list[ReportedMatch]:
    """Sort findings so the ones worth reading come first."""

    return sorted(matches, key=_key)


def rank_report(report: InstrumentReport) -> InstrumentReport:
    """Return ``report`` with its findings ranked."""

    return replace(report, matches=tuple(rank(report.matches)))


def filter_report(
    report: InstrumentReport,
    *,
    min_confidence: Confidence | None = None,
    crossing_only: bool = False,
    limit: int | None = None,
) -> InstrumentReport:
    """Rank and narrow a report without recomputing anything.

    The summary is deliberately left alone. It describes the document, not the
    subset that survived a display filter, and recomputing it here would make
    two reports of the same run disagree about how much text was reused.
    """

    if limit is not None and limit < 0:
        raise ConfigError("limit cannot be negative", limit=limit)
    floor = CONFIDENCE_ORDER[min_confidence] if min_confidence is not None else None
    kept: list[ReportedMatch] = []
    for match in rank(report.matches):
        if floor is not None and CONFIDENCE_ORDER[match.confidence] > floor:
            continue
        if crossing_only and match.other_key.jurisdiction == report.key.jurisdiction:
            continue
        kept.append(match)
    if limit is not None:
        kept = kept[:limit]
    return replace(report, matches=tuple(kept))


def apply_report_config(
    report: InstrumentReport, settings: ReportConfig | None = None
) -> InstrumentReport:
    """Narrow a report the way the configuration says to."""

    config = settings or ReportConfig()
    floor = Confidence(config.min_confidence)
    return filter_report(
        report,
        min_confidence=None if floor is Confidence.WEAK else floor,
        crossing_only=config.crossing_only,
        limit=config.top,
    )

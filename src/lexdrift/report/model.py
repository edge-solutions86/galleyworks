"""The report a person actually reads.

A :class:`SegmentMatch` is the engine's answer and it is not readable: it holds
references and spans. A report resolves those into quoted text on both sides,
because a reuse finding that cannot be checked against the words is a number
nobody should act on.

Quoting the other side needs the other document, which the matcher never has.
The caller supplies a :class:`TextSource`; when it cannot produce a document the
passage is still reported, with an empty quotation and ``resolved`` false, so a
missing corpus file shows up as a gap rather than as a silently shorter report.
"""

from __future__ import annotations

from collections.abc import Iterable, Mapping
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Protocol, runtime_checkable

from lexdrift.identity import InstrumentKey
from lexdrift.match.engine import SegmentMatch
from lexdrift.models import Instrument
from lexdrift.util.spans import TextSpan, coverage

__all__ = [
    "InstrumentReport",
    "MappingTextSource",
    "ReportSummary",
    "ReportedMatch",
    "ReportedPassage",
    "TextSource",
    "build_report",
]


@runtime_checkable
class TextSource(Protocol):
    """Supplies the raw text of an indexed instrument."""

    def text_of(self, key: InstrumentKey) -> str | None:
        """The raw text of ``key``, or ``None`` when it is not available."""


class MappingTextSource:
    """A text source backed by instruments already in memory."""

    def __init__(self, instruments: Iterable[Instrument] = ()) -> None:
        self._by_slug: dict[str, str] = {
            instrument.key.slug: instrument.raw_text for instrument in instruments
        }

    def add(self, instrument: Instrument) -> None:
        self._by_slug[instrument.key.slug] = instrument.raw_text

    def text_of(self, key: InstrumentKey) -> str | None:
        return self._by_slug.get(key.slug)


@dataclass(frozen=True, slots=True)
class ReportedPassage:
    """One passage, quoted on both sides."""

    query_span: TextSpan
    other_span: TextSpan
    query_text: str
    other_text: str
    resolved: bool

    @property
    def characters(self) -> int:
        return min(self.query_span.length, self.other_span.length)


@dataclass(frozen=True, slots=True)
class ReportedMatch:
    """One finding against one other instrument."""

    query_path: str
    other_key: InstrumentKey
    other_path: str
    containment: float
    jaccard: float
    passages: tuple[ReportedPassage, ...]

    @property
    def characters(self) -> int:
        return sum(passage.characters for passage in self.passages)

    @property
    def resolved(self) -> bool:
        """True when every passage could be quoted from the other document."""

        return all(passage.resolved for passage in self.passages)


@dataclass(frozen=True, slots=True)
class ReportSummary:
    """Totals for one report."""

    matched_segments: int
    total_segments: int
    reused_characters: int
    document_characters: int
    other_instruments: int

    @property
    def reuse_ratio(self) -> float:
        if self.document_characters == 0:
            return 0.0
        return min(1.0, self.reused_characters / self.document_characters)


@dataclass(frozen=True, slots=True)
class InstrumentReport:
    """Everything found about one instrument."""

    key: InstrumentKey
    title: str
    generated_at: datetime
    summary: ReportSummary
    matches: tuple[ReportedMatch, ...] = field(default_factory=tuple)

    @property
    def is_empty(self) -> bool:
        return not self.matches


def build_report(
    instrument: Instrument,
    matches: Iterable[SegmentMatch],
    source: TextSource | None = None,
    *,
    now: datetime | None = None,
) -> InstrumentReport:
    """Resolve matches into a readable report about ``instrument``."""

    resolver = source or MappingTextSource()
    reported: list[ReportedMatch] = []
    covered: list[TextSpan] = []
    matched_paths: set[str] = set()
    for match in matches:
        other_text = resolver.text_of(match.other.key)
        passages = tuple(
            ReportedPassage(
                query_span=passage.query,
                other_span=passage.other,
                query_text=passage.query.slice_of(instrument.raw_text),
                other_text="" if other_text is None else passage.other.slice_of(other_text),
                resolved=other_text is not None,
            )
            for passage in match.passages
        )
        covered.extend(passage.query for passage in match.passages)
        matched_paths.add(match.query_path)
        reported.append(
            ReportedMatch(
                query_path=match.query_path,
                other_key=match.other.key,
                other_path=match.other_path,
                containment=match.score.query_containment,
                jaccard=match.score.jaccard,
                passages=passages,
            )
        )
    summary = ReportSummary(
        matched_segments=len(matched_paths),
        total_segments=sum(1 for _ in instrument.body_segments()),
        reused_characters=coverage(covered),
        document_characters=len(instrument.raw_text),
        other_instruments=len({match.other_key.slug for match in reported}),
    )
    return InstrumentReport(
        key=instrument.key,
        title=instrument.title,
        generated_at=now or datetime.now(UTC),
        summary=summary,
        matches=tuple(reported),
    )


def group_by_instrument(report: InstrumentReport) -> Mapping[str, list[ReportedMatch]]:
    """Group a report's matches by the instrument they point at."""

    grouped: dict[str, list[ReportedMatch]] = {}
    for match in report.matches:
        grouped.setdefault(match.other_key.slug, []).append(match)
    return grouped

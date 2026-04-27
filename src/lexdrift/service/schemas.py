"""Request and response models for the HTTP service.

These are deliberately not the internal dataclasses. The engine's types change
whenever the algorithm does, and the wire format must not. Mapping between them
is a few lines here and it is the only place a rename can break a client.

Every response carries ``engine_digest``. Two deployments running different
shingle widths give different answers to the same question, and a client that
caches results needs to know which engine produced them.
"""

from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from lexdrift.identity import InstrumentKey
from lexdrift.match.explain import Confidence
from lexdrift.report.model import InstrumentReport, ReportedMatch, ReportedPassage

__all__ = [
    "HealthResponse",
    "InstrumentOut",
    "InstrumentDetail",
    "InstrumentsResponse",
    "SegmentOut",
    "MatchOut",
    "PassageOut",
    "QueryRequest",
    "ReportOut",
    "SummaryOut",
]


class _Model(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class QueryRequest(_Model):
    """Ask what one document reuses."""

    text: str = Field(min_length=1, max_length=4_000_000)
    jurisdiction: str = Field(min_length=1, max_length=32)
    session: str | None = Field(default=None, max_length=32)
    number: str | None = Field(default=None, max_length=32)
    format_name: str | None = Field(default=None, max_length=32)
    include_text: bool = True


class PassageOut(_Model):
    query_start: int
    query_end: int
    other_start: int
    other_end: int
    characters: int
    resolved: bool
    query_text: str | None = None
    other_text: str | None = None

    @classmethod
    def of(cls, passage: ReportedPassage, *, include_text: bool) -> PassageOut:
        return cls(
            query_start=passage.query_span.start,
            query_end=passage.query_span.end,
            other_start=passage.other_span.start,
            other_end=passage.other_span.end,
            characters=passage.characters,
            resolved=passage.resolved,
            query_text=passage.query_text if include_text else None,
            other_text=passage.other_text if include_text else None,
        )


class MatchOut(_Model):
    query_path: str
    other_instrument: str
    other_jurisdiction: str
    other_path: str
    containment: float
    jaccard: float
    characters: int
    confidence: Confidence
    headline: str
    reasons: tuple[str, ...]
    passages: tuple[PassageOut, ...]

    @classmethod
    def of(cls, match: ReportedMatch, *, include_text: bool) -> MatchOut:
        return cls(
            query_path=match.query_path,
            other_instrument=match.other_key.slug,
            other_jurisdiction=match.other_key.jurisdiction,
            other_path=match.other_path,
            containment=round(match.containment, 6),
            jaccard=round(match.jaccard, 6),
            characters=match.characters,
            confidence=match.confidence,
            headline=match.headline,
            reasons=match.reasons,
            passages=tuple(
                PassageOut.of(passage, include_text=include_text) for passage in match.passages
            ),
        )


class SummaryOut(_Model):
    matched_segments: int
    total_segments: int
    reused_characters: int
    document_characters: int
    other_instruments: int
    reuse_ratio: float


class ReportOut(_Model):
    instrument: str
    jurisdiction: str
    title: str
    generated_at: datetime
    engine_digest: str
    summary: SummaryOut
    matches: tuple[MatchOut, ...]

    @classmethod
    def of(cls, report: InstrumentReport, *, digest: str, include_text: bool = True) -> ReportOut:
        summary = report.summary
        return cls(
            instrument=report.key.slug,
            jurisdiction=report.key.jurisdiction,
            title=report.title,
            generated_at=report.generated_at,
            engine_digest=digest,
            summary=SummaryOut(
                matched_segments=summary.matched_segments,
                total_segments=summary.total_segments,
                reused_characters=summary.reused_characters,
                document_characters=summary.document_characters,
                other_instruments=summary.other_instruments,
                reuse_ratio=round(summary.reuse_ratio, 6),
            ),
            matches=tuple(
                MatchOut.of(match, include_text=include_text) for match in report.matches
            ),
        )


class InstrumentOut(_Model):
    """One indexed instrument."""

    key: str
    jurisdiction: str
    session: str
    chamber: str
    number: str

    @classmethod
    def of(cls, key: InstrumentKey) -> InstrumentOut:
        return cls(
            key=key.slug,
            jurisdiction=key.jurisdiction,
            session=key.session,
            chamber=key.chamber.value,
            number=key.number,
        )


class InstrumentsResponse(_Model):
    """One page of the corpus listing."""

    total: int
    offset: int
    limit: int
    instruments: tuple[InstrumentOut, ...]

    @property
    def has_more(self) -> bool:
        return self.offset + len(self.instruments) < self.total


class SegmentOut(_Model):
    """One indexed segment of an instrument."""

    index: int
    path: str
    tokens: int
    fingerprints: int
    content_ratio: float


class InstrumentDetail(_Model):
    """Everything the index holds about one instrument.

    The segment list is what makes a finding checkable from the outside: a
    client that has been told "1(a)(ii) matches" can ask which segments exist
    and how much text each one carries without holding the corpus.
    """

    instrument: InstrumentOut
    segments: tuple[SegmentOut, ...]

    @property
    def total_tokens(self) -> int:
        return sum(segment.tokens for segment in self.segments)


class HealthResponse(_Model):
    """What ``GET /health`` answers."""

    status: str
    version: str
    engine_digest: str
    instruments: int
    segments: int

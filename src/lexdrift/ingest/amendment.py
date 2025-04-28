"""Reader for amendment drafts carrying strike and insert markup.

The reader picks one reading, parses it with the ordinary enrolled parser, then
maps every segment span back onto the markup. The instrument therefore keeps the
marked-up text as its raw text, which is what an analyst wants to see quoted,
while the structure reflects the reading that was selected.
"""

from __future__ import annotations

from dataclasses import replace
from enum import StrEnum

from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.ingest.markup import MarkedText, has_markup, parse_markup
from lexdrift.models import Instrument, Segment
from lexdrift.util.offsets import OffsetMap

__all__ = ["AmendmentReader", "Reading"]


class Reading(StrEnum):
    """Which of the two texts a marked-up document describes."""

    CURRENT = "current"
    PROPOSED = "proposed"


class AmendmentReader:
    """Parses an amendment draft under one reading of its markup."""

    def __init__(self, reading: Reading = Reading.PROPOSED) -> None:
        self.reading = reading
        self.name = f"amendment-{reading.value}"
        self._inner = EnrolledReader()

    def sniff(self, document: SourceDocument) -> float:
        if not has_markup(document.text):
            return 0.0
        stripped = self._select(parse_markup(document.text))[0]
        inner = self._inner.sniff(_swap_text(document, stripped))
        if inner == 0.0:
            return 0.0
        return min(1.0, inner + 0.4)

    def read(self, document: SourceDocument) -> Instrument:
        parsed = parse_markup(document.text)
        body, mapping = self._select(parsed)
        instrument = self._inner.read(_swap_text(document, body))
        return Instrument(
            key=instrument.key,
            title=instrument.title,
            raw_text=document.text,
            segments=[_remap(segment, mapping) for segment in instrument.segments],
            introduced_on=instrument.introduced_on,
            enacted_on=instrument.enacted_on,
            source_format=self.name,
        )

    def _select(self, parsed: MarkedText) -> tuple[str, OffsetMap]:
        if self.reading is Reading.CURRENT:
            return parsed.current, parsed.current_map
        return parsed.proposed, parsed.proposed_map


def _swap_text(document: SourceDocument, text: str) -> SourceDocument:
    return SourceDocument(text=text, origin=document.origin, hints=dict(document.hints))


def _remap(segment: Segment, mapping: OffsetMap) -> Segment:
    return replace(segment, span=mapping.to_source_span(segment.span))

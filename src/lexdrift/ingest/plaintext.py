"""Fallback reader for documents with no recognisable legislative structure.

Every blank-line separated block becomes one section. It is the reader of last
resort, so its sniff score is deliberately low: anything else that recognises the
document should win.
"""

from __future__ import annotations

import re

from lexdrift.ingest.base import SourceDocument, key_from_hints
from lexdrift.models import Instrument, Segment, SegmentKind, SegmentPath
from lexdrift.util.spans import TextSpan

__all__ = ["PlaintextReader"]

_BLOCK_SPLIT = re.compile(r"\n[ \t]*\n")
_SNIFF_SCORE = 0.05


class PlaintextReader:
    """Reads unstructured text as a flat run of sections."""

    name = "plaintext"

    def sniff(self, document: SourceDocument) -> float:
        return _SNIFF_SCORE if document.text.strip() else 0.0

    def read(self, document: SourceDocument) -> Instrument:
        key = key_from_hints(document)
        text = document.text
        segments: list[Segment] = []
        for index, span in enumerate(_block_spans(text)):
            segments.append(
                Segment(
                    index=index,
                    kind=SegmentKind.SECTION,
                    path=SegmentPath((str(index + 1),)),
                    span=span,
                )
            )
        return Instrument(
            key=key,
            title=str(document.hint("title", _first_line(text))),
            raw_text=text,
            segments=segments,
            source_format=self.name,
        )


def _block_spans(text: str) -> list[TextSpan]:
    spans: list[TextSpan] = []
    cursor = 0
    for match in _BLOCK_SPLIT.finditer(text):
        spans.append(TextSpan(cursor, match.start()))
        cursor = match.end()
    spans.append(TextSpan(cursor, len(text)))
    return [span for span in (_trimmed(text, span) for span in spans) if not span.is_empty]


def _trimmed(text: str, span: TextSpan) -> TextSpan:
    start, end = span.start, span.end
    while start < end and text[start].isspace():
        start += 1
    while end > start and text[end - 1].isspace():
        end -= 1
    return TextSpan(start, end)


def _first_line(text: str) -> str:
    for line in text.split("\n"):
        stripped = line.strip()
        if stripped:
            return stripped
    return ""

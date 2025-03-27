"""Reader for enrolled bill text.

This first pass finds the top level only: the long title, the enacting clause
and the numbered sections. Label nesting is layered on top of it, so the section
boundaries are decided once and never revisited.
"""

from __future__ import annotations

from dataclasses import dataclass

from lexdrift.errors import ParseError
from lexdrift.ingest.base import SourceDocument, key_from_hints
from lexdrift.ingest.enrolled_lex import LexLine, LineKind, lex_lines
from lexdrift.models import Instrument, Segment, SegmentKind, SegmentPath
from lexdrift.util.spans import TextSpan

__all__ = ["EnrolledReader", "SectionBlock", "split_sections"]


@dataclass(frozen=True, slots=True)
class SectionBlock:
    """One numbered section and the lines that belong to it."""

    label: str
    heading: str
    span: TextSpan
    lines: tuple[LexLine, ...]


def split_sections(text: str, lines: list[LexLine]) -> list[SectionBlock]:
    """Cut the lexed lines into numbered sections."""

    openers = [line for line in lines if line.kind is LineKind.SECTION]
    if not openers:
        return []
    blocks: list[SectionBlock] = []
    for position, opener in enumerate(openers):
        following = openers[position + 1].span.start if position + 1 < len(openers) else len(text)
        owned = tuple(line for line in lines if opener.span.start <= line.span.start < following)
        blocks.append(
            SectionBlock(
                label=opener.label,
                heading=_heading_for(text, owned),
                span=TextSpan(opener.span.start, _trim_end(text, following)),
                lines=owned,
            )
        )
    return blocks


def _heading_for(text: str, lines: tuple[LexLine, ...]) -> str:
    for line in lines:
        if line.kind is LineKind.CATCHLINE and line.body is not None:
            return line.body.slice_of(text).strip()
    return ""


def _trim_end(text: str, end: int) -> int:
    while end > 0 and text[end - 1].isspace():
        end -= 1
    return end


class EnrolledReader:
    """Parses the enrolled text format used by state legislatures."""

    name = "enrolled"

    def sniff(self, document: SourceDocument) -> float:
        """Numbered sections are the gate; the other markers only add confidence."""

        lines = lex_lines(document.text)
        if not any(line.kind is LineKind.SECTION for line in lines):
            return 0.0
        score = 0.2
        head = "\n".join(document.first_lines)
        if "Be It Enacted" in head:
            score += 0.5
        if "AN ACT" in head:
            score += 0.3
        return min(score, 1.0)

    def read(self, document: SourceDocument) -> Instrument:
        key = key_from_hints(document)
        text = document.text
        lines = lex_lines(text)
        segments: list[Segment] = []
        for line in lines:
            if line.kind is LineKind.TITLE:
                segments.append(
                    Segment(
                        index=len(segments),
                        kind=SegmentKind.TITLE,
                        path=SegmentPath(),
                        span=line.span,
                    )
                )
            elif line.kind is LineKind.ENACTING:
                segments.append(
                    Segment(
                        index=len(segments),
                        kind=SegmentKind.ENACTING,
                        path=SegmentPath(),
                        span=line.span,
                    )
                )
        blocks = split_sections(text, lines)
        if not blocks:
            raise ParseError(
                "enrolled text has no numbered sections", line=1, origin=document.origin
            )
        for block in blocks:
            segments.append(
                Segment(
                    index=len(segments),
                    kind=SegmentKind.SECTION,
                    path=SegmentPath((block.label,)),
                    span=block.span,
                    heading=block.heading,
                )
            )
        return Instrument(
            key=key,
            title=str(document.hint("title", _long_title(text, lines))),
            raw_text=text,
            segments=segments,
            source_format=self.name,
        )


def _long_title(text: str, lines: list[LexLine]) -> str:
    for line in lines:
        if line.kind is LineKind.TITLE:
            return line.span.slice_of(text).strip()
    return ""

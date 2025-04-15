"""Reader for enrolled bill text.

Two passes. The first cuts the document into front matter and numbered sections,
which fixes the boundaries once. The second nests the labelled lines inside each
section, which is where the interesting decisions live.
"""

from __future__ import annotations

from lexdrift.errors import ParseError
from lexdrift.ingest.base import SourceDocument, resolve_key
from lexdrift.ingest.blocks import SectionBlock, split_sections
from lexdrift.ingest.enrolled_lex import LexLine, LineKind, lex_lines
from lexdrift.ingest.metadata import parse_effective_date, read_header
from lexdrift.ingest.structure import nest_section
from lexdrift.models import Instrument, Segment, SegmentKind, SegmentPath

__all__ = ["EnrolledReader"]

_FRONT_MATTER_KINDS: dict[LineKind, SegmentKind] = {
    LineKind.TITLE: SegmentKind.TITLE,
    LineKind.ENACTING: SegmentKind.ENACTING,
}


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
        text = document.text
        lines = lex_lines(text)
        blocks = split_sections(text, lines)
        if not blocks:
            raise ParseError(
                "enrolled text has no numbered sections", line=1, origin=document.origin
            )
        header = read_header(document.first_lines)
        segments = _front_matter(lines)
        for block in blocks:
            segments.extend(_section_tree(text, block, first_index=len(segments)))
        return Instrument(
            key=resolve_key(document, header),
            title=str(document.hint("title", _long_title(text, lines))),
            raw_text=text,
            segments=segments,
            enacted_on=parse_effective_date(text),
            source_format=self.name,
        )


def _front_matter(lines: list[LexLine]) -> list[Segment]:
    segments: list[Segment] = []
    for line in lines:
        kind = _FRONT_MATTER_KINDS.get(line.kind)
        if kind is None:
            continue
        segments.append(Segment(index=len(segments), kind=kind, path=SegmentPath(), span=line.span))
    return segments


def _section_tree(text: str, block: SectionBlock, *, first_index: int) -> list[Segment]:
    path = SegmentPath((block.label,))
    head = Segment(
        index=first_index,
        kind=SegmentKind.SECTION,
        path=path,
        span=block.span,
        heading=block.heading,
    )
    children = nest_section(
        text,
        block,
        first_index=first_index + 1,
        parent=first_index,
        parent_path=path,
    )
    return [head, *children]


def _long_title(text: str, lines: list[LexLine]) -> str:
    for line in lines:
        if line.kind is LineKind.TITLE:
            return line.span.slice_of(text).strip()
    return ""

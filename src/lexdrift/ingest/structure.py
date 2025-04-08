"""Nesting labelled lines into a segment tree.

A labelled line owns everything up to the next line at its own depth or
shallower, so a subsection's span contains its paragraphs. That is what a reader
of the statute means by "subsection (a)", and it is what a reuse report has to
quote.
"""

from __future__ import annotations

from dataclasses import dataclass

from lexdrift.errors import ParseError
from lexdrift.ingest.blocks import SectionBlock
from lexdrift.ingest.enrolled_lex import LexLine, LineKind
from lexdrift.ingest.labels import alpha_successor, roman_successor
from lexdrift.models import Segment, SegmentKind, SegmentPath
from lexdrift.util.spans import TextSpan

__all__ = ["nest_section", "resolve_ambiguous"]

_KIND_FOR_LINE: dict[LineKind, SegmentKind] = {
    LineKind.SUBSECTION: SegmentKind.SUBSECTION,
    LineKind.PARAGRAPH: SegmentKind.PARAGRAPH,
    LineKind.SUBPARAGRAPH: SegmentKind.SUBPARAGRAPH,
    LineKind.ITEM: SegmentKind.ITEM,
}


@dataclass(slots=True)
class _Frame:
    """An open label list."""

    kind: SegmentKind
    index: int
    last_label: str
    indent: int
    path: SegmentPath


def _successor(kind: SegmentKind, label: str) -> str | None:
    try:
        if kind is SegmentKind.PARAGRAPH:
            return roman_successor(label)
        if kind is SegmentKind.SUBSECTION:
            return alpha_successor(label)
    except ParseError:
        return None
    return None


def resolve_ambiguous(label: str, frames: list[_Frame], indent: int) -> SegmentKind:
    """Decide whether an ambiguous label continues a list or opens a nested one.

    Continuation wins over nesting: a subsection list sitting at ``(h)`` claims
    ``(i)`` before any roman reading is considered.
    """

    for frame in reversed(frames):
        if _successor(frame.kind, frame.last_label) == label:
            return frame.kind
    open_subsection = next(
        (frame for frame in frames if frame.kind is SegmentKind.SUBSECTION), None
    )
    if open_subsection is None:
        return SegmentKind.SUBSECTION
    if label == "i" or indent > open_subsection.indent:
        return SegmentKind.PARAGRAPH
    return SegmentKind.SUBSECTION


def nest_section(
    text: str,
    block: SectionBlock,
    *,
    first_index: int,
    parent: int,
    parent_path: SegmentPath,
) -> list[Segment]:
    """Build the labelled descendants of one section, in document order."""

    labelled = [
        line
        for line in block.lines
        if line.kind in _KIND_FOR_LINE or line.kind is LineKind.AMBIGUOUS_LABEL
    ]
    if not labelled:
        return []
    kinds = _assign_kinds(labelled)
    segments: list[Segment] = []
    frames: list[_Frame] = []
    for position, (line, kind) in enumerate(zip(labelled, kinds, strict=True)):
        while frames and frames[-1].kind.depth >= kind.depth:
            frames.pop()
        owner = frames[-1] if frames else None
        index = first_index + position
        path = (owner.path if owner is not None else parent_path).child(line.label)
        segments.append(
            Segment(
                index=index,
                kind=kind,
                path=path,
                span=_span_for(text, block, labelled, kinds, position),
                parent=owner.index if owner is not None else parent,
            )
        )
        frames.append(
            _Frame(kind=kind, index=index, last_label=line.label, indent=line.indent, path=path)
        )
    return segments


def _assign_kinds(labelled: list[LexLine]) -> list[SegmentKind]:
    kinds: list[SegmentKind] = []
    frames: list[_Frame] = []
    for line in labelled:
        if line.kind is LineKind.AMBIGUOUS_LABEL:
            kind = resolve_ambiguous(line.label, frames, line.indent)
        else:
            kind = _KIND_FOR_LINE[line.kind]
        kinds.append(kind)
        while frames and frames[-1].kind.depth >= kind.depth:
            frames.pop()
        frames.append(
            _Frame(
                kind=kind,
                index=len(kinds) - 1,
                last_label=line.label,
                indent=line.indent,
                path=SegmentPath(),
            )
        )
    return kinds


def _span_for(
    text: str,
    block: SectionBlock,
    labelled: list[LexLine],
    kinds: list[SegmentKind],
    position: int,
) -> TextSpan:
    start = labelled[position].span.start
    end = block.span.end
    own_depth = kinds[position].depth
    for later in range(position + 1, len(labelled)):
        if kinds[later].depth <= own_depth:
            end = labelled[later].span.start
            break
    while end > start and text[end - 1].isspace():
        end -= 1
    return TextSpan(start, end)

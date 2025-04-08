"""Cutting lexed lines into numbered sections.

This sits below both the reader and the structure builder so neither has to
import the other. The split is deliberately dumb: a section starts at a
``Section N.`` line and ends where the next one starts.
"""

from __future__ import annotations

from dataclasses import dataclass

from lexdrift.ingest.enrolled_lex import LexLine, LineKind
from lexdrift.util.spans import TextSpan

__all__ = ["SectionBlock", "split_sections"]


@dataclass(frozen=True, slots=True)
class SectionBlock:
    """One numbered section and the lines that belong to it."""

    label: str
    heading: str
    span: TextSpan
    lines: tuple[LexLine, ...]

    @property
    def is_empty(self) -> bool:
        return self.span.is_empty


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
                span=TextSpan(opener.span.start, trim_end(text, following)),
                lines=owned,
            )
        )
    return blocks


def _heading_for(text: str, lines: tuple[LexLine, ...]) -> str:
    for line in lines:
        if line.kind is LineKind.CATCHLINE and line.body is not None:
            return line.body.slice_of(text).strip()
    return ""


def trim_end(text: str, end: int) -> int:
    """Move ``end`` back over trailing whitespace."""

    while end > 0 and text[end - 1].isspace():
        end -= 1
    return end

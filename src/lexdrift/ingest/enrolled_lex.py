"""Line lexer for enrolled bill text.

Enrolled text is line oriented and the structure lives in the left margin: a
section opener, a statute catchline, and parenthesised labels that nest. The
lexer only classifies lines and records their spans; deciding what nests inside
what is the parser's job, because that decision needs lookahead the lexer does
not have.

The awkward case is ``(i)``. It is the ninth subsection label and it is also the
first roman paragraph label, and nothing about the line itself resolves that.
The lexer reports :attr:`LineKind.AMBIGUOUS_LABEL` and refuses to guess.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import StrEnum

from lexdrift.util.spans import TextSpan

__all__ = [
    "LexLine",
    "LineKind",
    "lex_lines",
]


class LineKind(StrEnum):
    BLANK = "blank"
    TITLE = "title"
    ENACTING = "enacting"
    SECTION = "section"
    CATCHLINE = "catchline"
    SUBSECTION = "subsection"
    AMBIGUOUS_LABEL = "ambiguous_label"
    PARAGRAPH = "paragraph"
    SUBPARAGRAPH = "subparagraph"
    ITEM = "item"
    TEXT = "text"


@dataclass(frozen=True, slots=True)
class LexLine:
    """One classified source line."""

    number: int
    kind: LineKind
    span: TextSpan
    indent: int
    label: str = ""
    body: TextSpan | None = None

    @property
    def has_label(self) -> bool:
        return self.kind in _LABELLED_KINDS


_LABELLED_KINDS = frozenset(
    {
        LineKind.SECTION,
        LineKind.SUBSECTION,
        LineKind.AMBIGUOUS_LABEL,
        LineKind.PARAGRAPH,
        LineKind.SUBPARAGRAPH,
        LineKind.ITEM,
    }
)

_SECTION_RE = re.compile(r"^Section\s+(?P<label>\d+)\s*\.\s*", re.IGNORECASE)
_CATCHLINE_RE = re.compile(r"^(?P<label>\d+(?:-\w+)+)\s*\.\s+\S")
_LABEL_RE = re.compile(r"^\((?P<label>[A-Za-z0-9]{1,6})\)\s*")
_TITLE_RE = re.compile(r"^AN ACT\b")
_ENACTING_RE = re.compile(r"^Be It Enacted\b", re.IGNORECASE)

_ROMAN_ONLY = re.compile(r"^[ivxlcdm]+$")
_LOWER_ALPHA = re.compile(r"^[a-z]{1,3}$")
_UPPER_ALPHA = re.compile(r"^[A-Z]{1,3}$")
_DIGITS = re.compile(r"^\d{1,4}$")
_AMBIGUOUS_LABELS = frozenset({"i", "v", "x", "l", "c", "d", "m"})


def _classify_label(label: str) -> LineKind:
    if _DIGITS.match(label):
        return LineKind.ITEM
    if _UPPER_ALPHA.match(label):
        return LineKind.SUBPARAGRAPH
    if not _LOWER_ALPHA.match(label):
        return LineKind.TEXT
    if label in _AMBIGUOUS_LABELS:
        return LineKind.AMBIGUOUS_LABEL
    if _ROMAN_ONLY.match(label):
        return LineKind.PARAGRAPH
    return LineKind.SUBSECTION


def lex_lines(text: str) -> list[LexLine]:
    """Classify every line of ``text``, keeping spans into the original.

    A parenthesised label only counts when it opens a block, that is, at the top
    of the document or after a blank line. Enrolled text wraps at the margin, so
    ``sixty\\n(60) days`` would otherwise read as an item label.
    """

    lines: list[LexLine] = []
    cursor = 0
    at_block_start = True
    for number, raw in enumerate(text.split("\n"), start=1):
        span = TextSpan(cursor, cursor + len(raw))
        cursor = span.end + 1
        stripped = raw.strip()
        indent = len(raw) - len(raw.lstrip())
        if not stripped:
            lines.append(LexLine(number=number, kind=LineKind.BLANK, span=span, indent=0))
            at_block_start = True
            continue
        content_start = span.start + indent
        lines.append(_classify(number, stripped, span, indent, content_start, at_block_start))
        at_block_start = False
    return lines


def _classify(
    number: int,
    stripped: str,
    span: TextSpan,
    indent: int,
    start: int,
    at_block_start: bool,
) -> LexLine:
    section = _SECTION_RE.match(stripped)
    if section is not None:
        return LexLine(
            number=number,
            kind=LineKind.SECTION,
            span=span,
            indent=indent,
            label=section["label"],
            body=TextSpan(start + section.end(), span.end),
        )
    catchline = _CATCHLINE_RE.match(stripped)
    if catchline is not None:
        return LexLine(
            number=number,
            kind=LineKind.CATCHLINE,
            span=span,
            indent=indent,
            label=catchline["label"],
            body=TextSpan(start + catchline.end() - 1, span.end),
        )
    if _TITLE_RE.match(stripped):
        return LexLine(number=number, kind=LineKind.TITLE, span=span, indent=indent)
    if _ENACTING_RE.match(stripped):
        return LexLine(number=number, kind=LineKind.ENACTING, span=span, indent=indent)
    labelled = _LABEL_RE.match(stripped) if at_block_start else None
    if labelled is not None:
        kind = _classify_label(labelled["label"])
        if kind is not LineKind.TEXT:
            return LexLine(
                number=number,
                kind=kind,
                span=span,
                indent=indent,
                label=labelled["label"],
                body=TextSpan(start + labelled.end(), span.end),
            )
    return LexLine(number=number, kind=LineKind.TEXT, span=span, indent=indent)

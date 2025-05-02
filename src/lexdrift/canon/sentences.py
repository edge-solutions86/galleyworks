"""Sentence segmentation for legislative prose.

Ordinary sentence splitters cut ``W.S. 41-3-101`` into three sentences and
``No. 1188`` into two. Legislative text is dense with those forms, so the
splitter carries an explicit abbreviation list and refuses to break after one.

Statutory paragraphs also end on a semicolon followed by ``and`` or ``or``. That
is a sentence boundary for our purposes, because the clause it closes is the
unit a drafter copies, but it is off by default since running prose uses the
same punctuation differently.
"""

from __future__ import annotations

import re

from lexdrift.util.spans import TextSpan

__all__ = ["ABBREVIATIONS", "split_sentences"]

ABBREVIATIONS = frozenset(
    {
        "art",
        "ch",
        "cf",
        "co",
        "corp",
        "dept",
        "div",
        "e.g",
        "et al",
        "et seq",
        "ex",
        "fig",
        "i.e",
        "inc",
        "no",
        "para",
        "pt",
        "sec",
        "seq",
        "subd",
        "subsec",
        "supp",
        "u.s.c",
        "v",
        "viz",
        "vol",
        "w.s",
    }
)

_TERMINATOR = re.compile(r"[.?!]")
_SEMICOLON_CLAUSE = re.compile(r";\s+(?:and|or)\b", re.IGNORECASE)
_WORD_BEFORE = re.compile(r"([A-Za-z][A-Za-z.]*)\s*$")
_SINGLE_INITIAL = re.compile(r"^[A-Za-z]$")


def _is_abbreviation(text: str, dot: int) -> bool:
    """True when the period at ``dot`` closes an abbreviation rather than a sentence."""

    match = _WORD_BEFORE.search(text, 0, dot)
    if match is None:
        return False
    word = match.group(1).lower().rstrip(".")
    if _SINGLE_INITIAL.match(word):
        return True
    return word in ABBREVIATIONS


def _starts_new_sentence(text: str, position: int) -> bool:
    while position < len(text) and text[position].isspace():
        position += 1
    if position >= len(text):
        return True
    char = text[position]
    return char.isupper() or char.isdigit() or char in "(\"'“‘"


def split_sentences(
    text: str,
    *,
    offset: int = 0,
    break_on_clause: bool = False,
) -> list[TextSpan]:
    """Return the sentence spans of ``text``, shifted by ``offset``."""

    boundaries = sorted(_boundaries(text, break_on_clause=break_on_clause))
    spans: list[TextSpan] = []
    start = 0
    for boundary in [*boundaries, len(text)]:
        piece = _trim(text, start, boundary)
        if piece is not None:
            spans.append(piece.shift(offset))
        start = boundary
    return spans


def _boundaries(text: str, *, break_on_clause: bool) -> set[int]:
    found: set[int] = set()
    for match in _TERMINATOR.finditer(text):
        dot = match.start()
        if text[dot] == "." and _is_abbreviation(text, dot):
            continue
        end = match.end()
        while end < len(text) and text[end] in ")\"'”’":
            end += 1
        if end >= len(text):
            continue
        if not text[end].isspace():
            continue
        if _starts_new_sentence(text, end):
            found.add(end)
    if break_on_clause:
        for match in _SEMICOLON_CLAUSE.finditer(text):
            found.add(match.start() + 1)
    return found


def _trim(text: str, start: int, end: int) -> TextSpan | None:
    while start < end and text[start].isspace():
        start += 1
    while end > start and text[end - 1].isspace():
        end -= 1
    if start >= end:
        return None
    return TextSpan(start, end)

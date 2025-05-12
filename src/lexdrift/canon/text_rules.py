"""The three rules that run before anything looks at meaning.

Typesetting differences are the largest single source of false negatives in a
reuse corpus: one chamber publishes curly quotes and an en dash, another
publishes ASCII, and the same sentence fingerprints differently. These rules
flatten that before any structural rule runs.

Case folding is last of the three, because the citation and enumeration rules
that come later match on capitalisation.
"""

from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable, Iterator

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = [
    "CaseFoldRule",
    "PUNCTUATION_FOLD",
    "UnicodeFoldRule",
    "WhitespaceRule",
    "runs_where_changed",
]

PUNCTUATION_FOLD: dict[str, str] = {
    "‘": "'",
    "’": "'",
    "‚": "'",
    "“": '"',
    "”": '"',
    "„": '"',
    "‐": "-",
    "‑": "-",
    "‒": "-",
    "–": "-",
    "—": "-",
    "―": "-",
    "…": "...",
    " ": " ",
    " ": " ",
    " ": " ",
    "﻿": "",
    "­": "",
}

_WHITESPACE_RUN = re.compile(r"\s{2,}|[\t\r\f\v\n]")


def runs_where_changed(text: str, folded: list[str]) -> Iterator[Edit]:
    """Emit one edit per maximal run where ``folded`` differs from ``text``.

    ``folded`` holds one replacement string per source character, so a fold that
    lengthens or drops characters still lines up with its source offsets.
    """

    start: int | None = None
    for index, char in enumerate(text):
        if folded[index] != char:
            if start is None:
                start = index
        elif start is not None:
            yield Edit(TextSpan(start, index), "".join(folded[start:index]))
            start = None
    if start is not None:
        yield Edit(TextSpan(start, len(text)), "".join(folded[start:]))


class UnicodeFoldRule:
    """Normalize compatibility forms and typographic punctuation to ASCII."""

    name = "unicode-fold"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.fold_unicode:
            return ()
        folded = [self._fold(char) for char in text]
        return runs_where_changed(text, folded)

    @staticmethod
    def _fold(char: str) -> str:
        mapped = PUNCTUATION_FOLD.get(char)
        if mapped is not None:
            return mapped
        decomposed = unicodedata.normalize("NFKC", char)
        return decomposed if decomposed.isprintable() or decomposed.isspace() else char


class WhitespaceRule:
    """Collapse every run of whitespace to a single space."""

    name = "whitespace"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        for match in _WHITESPACE_RUN.finditer(text):
            span = TextSpan(match.start(), match.end())
            if span.length == 1 and text[span.start] == " ":
                continue
            yield Edit(span, " ")


class CaseFoldRule:
    """Lowercase the text, keeping every offset accounted for."""

    name = "case-fold"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.fold_case:
            return ()
        folded = [char.lower() for char in text]
        return runs_where_changed(text, folded)

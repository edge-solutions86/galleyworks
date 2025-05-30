"""Defined terms and the quotation marks around them.

A definitions section reads ``"Board" means the state board of control``, and the
term is then quoted, unquoted or capitalised at every later use depending on the
drafter. The engine strips the quotes from any phrase the document itself
defines, so ``"board"`` and ``board`` fingerprint alike.

Only phrases the document defines are touched. Stripping every quoted phrase
would destroy the difference between a quotation and a definition, which is
exactly the difference a reuse report is asked about.
"""

from __future__ import annotations

import re
from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = [
    "DefinedTermRule",
    "Definition",
    "find_definitions",
]

_QUOTE_PAIRS = ('""', "''", "“”", "‘’")
_OPENERS = "".join(pair[0] for pair in _QUOTE_PAIRS)
_CLOSERS = "".join(pair[1] for pair in _QUOTE_PAIRS)
_DEFINITION_RE = re.compile(
    rf"[{_OPENERS}](?P<term>[^{_CLOSERS}]{{1,80}})[{_CLOSERS}]\s+means\b",
    re.IGNORECASE,
)
_MAX_TERM_WORDS = 6


@dataclass(frozen=True, slots=True)
class Definition:
    """A term the document defines, and where it does so."""

    term: str
    span: TextSpan

    @property
    def key(self) -> str:
        return " ".join(self.term.lower().split())


def find_definitions(text: str) -> list[Definition]:
    """Every ``"term" means`` definition in ``text``, in document order."""

    found: list[Definition] = []
    for match in _DEFINITION_RE.finditer(text):
        term = match["term"].strip()
        if not term or len(term.split()) > _MAX_TERM_WORDS:
            continue
        found.append(Definition(term=term, span=TextSpan(match.start("term"), match.end("term"))))
    return found


def _quoted_occurrences(text: str, terms: set[str]) -> Iterator[Edit]:
    pattern = re.compile(
        rf"[{_OPENERS}](?P<term>[^{_CLOSERS}]{{1,80}})[{_CLOSERS}]",
        re.IGNORECASE,
    )
    for match in pattern.finditer(text):
        inner = match["term"]
        if " ".join(inner.lower().split()) not in terms:
            continue
        yield Edit(TextSpan(match.start(), match.end()), inner)


class DefinedTermRule:
    """Strips quotation marks from terms the document defines."""

    name = "defined-terms"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        definitions = find_definitions(text)
        if not definitions:
            return ()
        return _quoted_occurrences(text, {definition.key for definition in definitions})

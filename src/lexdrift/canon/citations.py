"""Statutory citation normalization.

The same provision is cited half a dozen ways depending on who is drafting:
``W.S. 41-3-101``, ``Wyo. Stat. Ann. § 41-3-101``, ``section 41-3-101`` and a
bare ``§ 41-3-101`` all point at one section. Left alone they fingerprint as
different text, and a clause copied between two states looks original because
its citations were re-styled on the way.

Every recognised form is rewritten to ``cite <reporter> <number><subsections>``.
The reporter is kept, because 41-3-101 in a state code and 41-3-101 in a
municipal code are not the same provision and must not collide.
"""

from __future__ import annotations

import re
from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = [
    "Citation",
    "CitationRule",
    "LOCAL_REPORTER",
    "find_citations",
]

LOCAL_REPORTER = "local"

_REPORTER_CODES: tuple[tuple[str, str], ...] = (
    ("usc", "usc"),
    ("cfr", "cfr"),
    ("wyostatann", "wy"),
    ("wyostat", "wy"),
    ("ws", "wy"),
)

_NUMBER = r"\d+(?:[-.]\d+){0,3}[a-z]?"
_SUBSECTIONS = r"(?:\([A-Za-z0-9]{1,5}\))*"
_CITE_RE = re.compile(
    r"(?:(?P<title>\d{1,2})\s+)?"
    r"(?:(?P<reporter>w\.?\s?s\.?|wyo\.?\s?stat\.?(?:\s?ann\.?)?|u\.?\s?s\.?\s?c\.?|c\.?\s?f\.?\s?r\.?)"
    r"|(?P<word>sections?|secs?\.?|§{1,2}))"
    r"\s*(?:§{1,2}\s*)?"
    rf"(?P<number>{_NUMBER})"
    rf"(?P<subs>{_SUBSECTIONS})",
    re.IGNORECASE,
)
_SEPARATED = re.compile(r"\d[-.]\d")


@dataclass(frozen=True, slots=True)
class Citation:
    """A recognised statutory reference."""

    span: TextSpan
    reporter: str
    number: str
    subsections: str
    title: str | None = None

    @property
    def canonical(self) -> str:
        reporter = self.reporter if self.title is None else f"{self.reporter}{self.title}"
        return f"cite {reporter} {self.number}{self.subsections}"


def _reporter_code(raw: str | None) -> str:
    if raw is None:
        return LOCAL_REPORTER
    squashed = re.sub(r"[^a-z]", "", raw.lower())
    for prefix, code in _REPORTER_CODES:
        if squashed == prefix:
            return code
    return squashed or LOCAL_REPORTER


def find_citations(text: str) -> Iterator[Citation]:
    """Yield every citation in ``text``, left to right."""

    for match in _CITE_RE.finditer(text):
        reporter = _reporter_code(match["reporter"])
        title = match["title"]
        if title is not None and reporter == LOCAL_REPORTER:
            # A bare number in front of "section" is a count, not a title.
            continue
        if reporter == LOCAL_REPORTER and not _SEPARATED.search(match["number"]):
            # "section 4" is a cross-reference inside one act, not a citation.
            continue
        yield Citation(
            span=TextSpan(match.start(), match.end()),
            reporter=reporter,
            number=match["number"].lower(),
            subsections=(match["subs"] or "").lower(),
            title=title,
        )


class CitationRule:
    """Rewrites recognised citations into their canonical form."""

    name = "citations"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.normalize_citations:
            return
        for citation in find_citations(text):
            yield Edit(citation.span, citation.canonical)

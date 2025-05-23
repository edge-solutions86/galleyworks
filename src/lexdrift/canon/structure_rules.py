"""Rules that remove structure the segment tree already records.

Enumeration labels and the enacting clause are both navigation, not substance. A
clause copied from Wyoming into Montana arrives as ``(b)`` where it left as
``(a)``, and every act in a state opens with the same enacting sentence, which
would otherwise be the strongest match in the corpus.

Both are dropped from the canonical text and neither is lost: the label survives
on the segment path, and the enacting clause survives in the raw text.
"""

from __future__ import annotations

import re
from collections.abc import Iterable, Iterator

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = [
    "EnactingClauseRule",
    "EnumerationRule",
    "find_enumeration_labels",
]

_LABEL_RE = re.compile(r"\((?P<label>[A-Za-z0-9]{1,4})\)\s*")
_OPENERS = (". ", "; ", ": ", ") ", ".\n", ";\n")
_ENACTING_RE = re.compile(
    r"be it enacted[^.:]{0,200}[.:]\s*",
    re.IGNORECASE,
)


def _opens_a_block(text: str, start: int) -> bool:
    if start == 0:
        return True
    return any(text.startswith(opener, start - len(opener)) for opener in _OPENERS)


def find_enumeration_labels(text: str) -> Iterator[TextSpan]:
    """Yield the spans of labels that introduce a block rather than cite one."""

    for match in _LABEL_RE.finditer(text):
        if _opens_a_block(text, match.start()):
            yield TextSpan(match.start(), match.end())


class EnumerationRule:
    """Drops ``(a)``, ``(ii)`` and ``(3)`` labels that open a block."""

    name = "enumeration"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.drop_enumeration_labels:
            return
        for span in find_enumeration_labels(text):
            yield Edit(span, "")


class EnactingClauseRule:
    """Drops the enacting clause every act in a jurisdiction shares."""

    name = "enacting-clause"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.drop_enacting_clause:
            return
        for match in _ENACTING_RE.finditer(text):
            yield Edit(TextSpan(match.start(), match.end()), "")

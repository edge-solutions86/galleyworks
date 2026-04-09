"""Possessives and optional hyphens.

Two forms that carry no legal difference and change on re-typesetting.
``the board's schedule`` and ``the schedule of the board`` are different
drafting, and this rule does not touch that; what it folds is ``board's``
against ``boards'`` against ``board s``, which is one word set three ways by
three typesetters.

Optional hyphens are the same shape of problem. ``non-resident``,
``nonresident`` and ``non resident`` appear in the same chapter of the same code,
and only the first two are ever intended as one word. The prefix list is short
and closed on purpose: folding every hyphen would join ``water-right holder``
into a word that appears nowhere.
"""

from __future__ import annotations

import re
from collections.abc import Iterable

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = ["JOINED_PREFIXES", "MorphologyRule", "fold_possessive"]

JOINED_PREFIXES = (
    "anti",
    "co",
    "counter",
    "inter",
    "intra",
    "multi",
    "non",
    "post",
    "pre",
    "re",
    "semi",
    "sub",
    "super",
    "un",
)

# The trailing guard is a negative lookahead rather than a word boundary:
# after a plural possessive the apostrophe is the last character, and a
# boundary between two non-word characters does not exist.
_POSSESSIVE_RE = re.compile(r"\b([A-Za-z]{2,})['’]s?(?![\w'’])")
_PREFIX_RE = re.compile(
    r"\b(" + "|".join(JOINED_PREFIXES) + r")[-‐‑]([a-z]{3,})\b",
    re.IGNORECASE,
)


def fold_possessive(word: str) -> str:
    """Strip a possessive ending from ``word``."""

    return _POSSESSIVE_RE.sub(lambda match: match.group(1), word)


class MorphologyRule:
    """Drops possessive endings and closes hyphenated prefixes."""

    name = "morphology"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.fold_variants:
            return
        claimed: list[TextSpan] = []
        for match in _POSSESSIVE_RE.finditer(text):
            span = TextSpan(match.start(), match.end())
            claimed.append(span)
            yield Edit(span, match.group(1))
        for match in _PREFIX_RE.finditer(text):
            span = TextSpan(match.start(), match.end())
            if any(span.overlaps(taken) for taken in claimed):
                continue
            yield Edit(span, f"{match.group(1)}{match.group(2)}")

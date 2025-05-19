"""Normalizing the quantities legislative text is full of.

Drafters write a quantity twice: ``sixty (60) days``, ``one hundred dollars
($100.00)``, ``five percent (5%)``. Two chambers pick different halves of that
pair when they re-typeset, and money is written with and without cents in the
same act. All of it has to collapse to one form before fingerprinting.

Patterns are tried in priority order and a later pattern is skipped where it
would overlap an earlier match, so ``one hundred dollars ($100.00)`` is claimed
once by the money rule rather than twice by money and by the doubled-numeral
rule.
"""

from __future__ import annotations

import re
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = ["MONTHS", "NumeralRule", "normalize_numerals"]

MONTHS: dict[str, int] = {
    "january": 1,
    "february": 2,
    "march": 3,
    "april": 4,
    "may": 5,
    "june": 6,
    "july": 7,
    "august": 8,
    "september": 9,
    "october": 10,
    "november": 11,
    "december": 12,
}

_NUMBER_WORDS = (
    "zero one two three four five six seven eight nine ten eleven twelve thirteen "
    "fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty "
    "sixty seventy eighty ninety hundred thousand million billion and"
).split()
_NUM_WORD = rf"(?:{'|'.join(_NUMBER_WORDS)})"
_WORDS = rf"\b{_NUM_WORD}(?:[\s-]+{_NUM_WORD})*"


def _digits(raw: str) -> str:
    cleaned = raw.replace(",", "")
    if cleaned.endswith(".00"):
        cleaned = cleaned[:-3]
    return cleaned


def _money(match: re.Match[str]) -> str:
    return f"usd {_digits(match['amount'])}"


def _percent(match: re.Match[str]) -> str:
    return f"{_digits(match['amount'])} pct"


def _date(match: re.Match[str]) -> str:
    month = MONTHS[match["month"].lower()]
    return f"date {int(match['year']):04d}-{month:02d}-{int(match['day']):02d}"


def _doubled(match: re.Match[str]) -> str:
    return _digits(match["amount"])


def _ordinal(match: re.Match[str]) -> str:
    return match["amount"]


@dataclass(frozen=True, slots=True)
class _Pattern:
    name: str
    regex: re.Pattern[str]
    render: Callable[[re.Match[str]], str]


_PATTERNS: tuple[_Pattern, ...] = (
    _Pattern(
        "money-doubled",
        re.compile(
            rf"{_WORDS}\s+dollars?\s*\(\s*\$\s*(?P<amount>[\d,]+(?:\.\d{{2}})?)\s*\)",
            re.IGNORECASE,
        ),
        _money,
    ),
    _Pattern(
        "money",
        re.compile(r"\$\s*(?P<amount>[\d,]+(?:\.\d{2})?)"),
        _money,
    ),
    _Pattern(
        "percent-doubled",
        re.compile(
            rf"{_WORDS}\s+per\s?cent(?:um)?\s*\(\s*(?P<amount>\d+(?:\.\d+)?)\s*%?\s*\)",
            re.IGNORECASE,
        ),
        _percent,
    ),
    _Pattern(
        "percent",
        re.compile(r"(?P<amount>\d+(?:\.\d+)?)\s*(?:%|per\s?cent(?:um)?|percent)", re.IGNORECASE),
        _percent,
    ),
    _Pattern(
        "date",
        re.compile(
            rf"(?P<month>{'|'.join(MONTHS)})\s+(?P<day>\d{{1,2}}),?\s+(?P<year>\d{{4}})",
            re.IGNORECASE,
        ),
        _date,
    ),
    _Pattern(
        "doubled-numeral",
        re.compile(rf"{_WORDS}\s*\(\s*(?P<amount>[\d,]+(?:\.\d+)?)\s*\)", re.IGNORECASE),
        _doubled,
    ),
    _Pattern(
        "ordinal",
        re.compile(r"\b(?P<amount>\d+)(?:st|nd|rd|th)\b", re.IGNORECASE),
        _ordinal,
    ),
)


def normalize_numerals(text: str) -> Iterator[Edit]:
    """Yield non-overlapping edits that put every quantity into one form."""

    claimed: list[TextSpan] = []
    found: list[Edit] = []
    for pattern in _PATTERNS:
        for match in pattern.regex.finditer(text):
            span = TextSpan(match.start(), match.end())
            if any(span.overlaps(taken) for taken in claimed):
                continue
            replacement = pattern.render(match)
            claimed.append(span)
            if replacement != span.slice_of(text):
                found.append(Edit(span, replacement))
    yield from sorted(found, key=lambda edit: edit.span.start)


class NumeralRule:
    """Rewrites money, percentages, dates and doubled numerals."""

    name = "numerals"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.normalize_numbers:
            return ()
        return normalize_numerals(text)

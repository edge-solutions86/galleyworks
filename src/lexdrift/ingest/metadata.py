"""Pulling identity and dates out of a bill's front matter.

Hints from the caller always win. This is the fallback for a corpus that arrives
as a directory of text files with nothing but the text, which is the normal case
when a legislature publishes bills as printed.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date

from lexdrift.identity import Chamber

__all__ = ["BillHeader", "parse_effective_date", "read_header"]

_MONTHS = {
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

_CHAMBER_WORDS: tuple[tuple[str, Chamber], ...] = (
    ("house bill", Chamber.LOWER),
    ("house joint resolution", Chamber.LOWER),
    ("senate file", Chamber.UPPER),
    ("senate bill", Chamber.UPPER),
    ("senate joint resolution", Chamber.UPPER),
    ("council bill", Chamber.COUNCIL),
    ("ordinance", Chamber.COUNCIL),
    ("executive order", Chamber.EXECUTIVE),
)

_NUMBER_RE = re.compile(
    r"(?:no\.?|number)\s*(?P<number>[A-Z]{0,3}[- ]?\d{1,5}(?:-\d{1,4})?[A-Z]?)",
    re.IGNORECASE,
)
_SESSION_RE = re.compile(r"\b(?P<year>(?:19|20)\d{2})\b")
_SPONSOR_RE = re.compile(r"^sponsored by:\s*(?P<sponsor>.+)$", re.IGNORECASE)
_EFFECTIVE_RE = re.compile(
    r"effective\s+(?P<month>[A-Za-z]+)\s+(?P<day>\d{1,2}),\s*(?P<year>\d{4})",
    re.IGNORECASE,
)


@dataclass(frozen=True, slots=True)
class BillHeader:
    """What the printed front matter says about a bill."""

    number: str | None = None
    chamber: Chamber | None = None
    session: str | None = None
    sponsor: str | None = None

    @property
    def is_complete(self) -> bool:
        return None not in (self.number, self.chamber, self.session)


def read_header(lines: list[str]) -> BillHeader:
    """Scan the opening lines of a bill for its printed identity."""

    number: str | None = None
    chamber: Chamber | None = None
    session: str | None = None
    sponsor: str | None = None
    for raw in lines:
        line = raw.strip()
        if not line:
            continue
        lowered = line.lower()
        if chamber is None:
            for word, found in _CHAMBER_WORDS:
                if word in lowered:
                    chamber = found
                    break
        if number is None and chamber is not None:
            match = _NUMBER_RE.search(line)
            if match is not None:
                number = match["number"].replace(" ", "").replace("-", "")
        if sponsor is None:
            sponsored = _SPONSOR_RE.match(line)
            if sponsored is not None:
                sponsor = sponsored["sponsor"].strip()
        if session is None:
            year = _SESSION_RE.search(line)
            if year is not None:
                session = year["year"]
    return BillHeader(number=number, chamber=chamber, session=session, sponsor=sponsor)


def parse_effective_date(text: str) -> date | None:
    """Find an ``effective July 1, 2025`` clause anywhere in the text."""

    match = _EFFECTIVE_RE.search(text)
    if match is None:
        return None
    month = _MONTHS.get(match["month"].lower())
    if month is None:
        return None
    try:
        return date(int(match["year"]), month, int(match["day"]))
    except ValueError:
        return None

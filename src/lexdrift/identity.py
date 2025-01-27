"""Stable identifiers for jurisdictions and legislative instruments.

An instrument key is the tuple that makes a bill unique across every corpus we
load: the jurisdiction that enacted it, the legislative session, the chamber of
origin and the printed number. It round-trips through a compact slug so it can
sit in a filename, a database row and a report without being escaped.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import StrEnum

from lexdrift.errors import ConfigError

__all__ = [
    "Chamber",
    "GovernmentLevel",
    "InstrumentKey",
    "Jurisdiction",
]

_SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,31}$")
_KEY_RE = re.compile(
    r"^(?P<jurisdiction>[a-z0-9][a-z0-9-]{0,31})"
    r"/(?P<session>[a-z0-9][a-z0-9-]{0,31})"
    r"/(?P<chamber>[a-z]+)"
    r"/(?P<number>[A-Za-z0-9._-]{1,32})$"
)


class GovernmentLevel(StrEnum):
    """How far up the hierarchy a jurisdiction sits."""

    FEDERAL = "federal"
    STATE = "state"
    COUNTY = "county"
    MUNICIPAL = "municipal"
    TRIBAL = "tribal"


class Chamber(StrEnum):
    """Chamber of origin for an instrument."""

    LOWER = "lower"
    UPPER = "upper"
    UNICAMERAL = "unicameral"
    COUNCIL = "council"
    EXECUTIVE = "executive"


@dataclass(frozen=True, slots=True, order=True)
class Jurisdiction:
    """A body that can enact text."""

    slug: str
    name: str
    level: GovernmentLevel
    parent: str | None = None

    def __post_init__(self) -> None:
        if not _SLUG_RE.match(self.slug):
            raise ConfigError("jurisdiction slug is not a valid identifier", slug=self.slug)
        if not self.name.strip():
            raise ConfigError("jurisdiction name is empty", slug=self.slug)
        if self.parent is not None and not _SLUG_RE.match(self.parent):
            raise ConfigError("jurisdiction parent is not a valid identifier", parent=self.parent)
        if self.parent == self.slug:
            raise ConfigError("jurisdiction is its own parent", slug=self.slug)

    @property
    def is_subordinate(self) -> bool:
        return self.parent is not None

    def ancestry(self, table: dict[str, Jurisdiction]) -> list[str]:
        """Walk ``parent`` links from this jurisdiction upward."""

        chain: list[str] = [self.slug]
        seen = {self.slug}
        cursor = self.parent
        while cursor is not None:
            if cursor in seen:
                raise ConfigError("jurisdiction ancestry contains a cycle", slug=cursor)
            seen.add(cursor)
            chain.append(cursor)
            found = table.get(cursor)
            if found is None:
                break
            cursor = found.parent
        return chain


@dataclass(frozen=True, slots=True, order=True)
class InstrumentKey:
    """Uniquely names one instrument inside one jurisdiction."""

    jurisdiction: str
    session: str
    chamber: Chamber
    number: str

    def __post_init__(self) -> None:
        if not _SLUG_RE.match(self.jurisdiction):
            raise ConfigError("instrument jurisdiction is malformed", value=self.jurisdiction)
        if not _SLUG_RE.match(self.session):
            raise ConfigError("instrument session is malformed", value=self.session)
        if not self.number or len(self.number) > 32:
            raise ConfigError("instrument number is malformed", value=self.number)

    @property
    def slug(self) -> str:
        return f"{self.jurisdiction}/{self.session}/{self.chamber.value}/{self.number}"

    @classmethod
    def parse(cls, slug: str) -> InstrumentKey:
        match = _KEY_RE.match(slug)
        if match is None:
            raise ConfigError("instrument key does not parse", slug=slug)
        try:
            chamber = Chamber(match["chamber"])
        except ValueError as exc:
            raise ConfigError("unknown chamber in instrument key", slug=slug) from exc
        return cls(
            jurisdiction=match["jurisdiction"],
            session=match["session"],
            chamber=chamber,
            number=match["number"],
        )

    def __str__(self) -> str:
        return self.slug

"""The jurisdiction table.

A corpus that mixes a state code with the ordinances of three of its cities
needs to know which is which: an ordinance that repeats a state statute is
compliance, and a state statute that repeats an ordinance is something else
entirely. The table records the hierarchy and the drafting profile each
jurisdiction is canonicalized under.

Cycles are rejected at load rather than at traversal. A cycle in a table of
twelve entries is a typo, and finding it when a report walks the ancestry means
finding it in the middle of a corpus run.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from lexdrift.canon.profiles import PROFILES
from lexdrift.errors import ConfigError
from lexdrift.identity import GovernmentLevel, Jurisdiction

__all__ = ["JurisdictionRegistry", "load_jurisdictions"]


@dataclass(slots=True)
class JurisdictionRegistry:
    """Every jurisdiction a corpus knows about."""

    _by_slug: dict[str, Jurisdiction] = field(default_factory=dict)
    _profiles: dict[str, str] = field(default_factory=dict)

    def __len__(self) -> int:
        return len(self._by_slug)

    def __contains__(self, slug: object) -> bool:
        return slug in self._by_slug

    def __iter__(self) -> Iterator[Jurisdiction]:
        return iter(sorted(self._by_slug.values()))

    def add(self, jurisdiction: Jurisdiction, *, profile: str = "default") -> None:
        """Register one jurisdiction."""

        if jurisdiction.slug in self._by_slug:
            raise ConfigError("jurisdiction is already registered", slug=jurisdiction.slug)
        if profile not in PROFILES:
            raise ConfigError(
                "unknown canonicalization profile",
                slug=jurisdiction.slug,
                profile=profile,
                known=",".join(sorted(PROFILES)),
            )
        self._by_slug[jurisdiction.slug] = jurisdiction
        self._profiles[jurisdiction.slug] = profile

    def get(self, slug: str) -> Jurisdiction:
        try:
            return self._by_slug[slug]
        except KeyError as exc:
            raise ConfigError("no such jurisdiction", slug=slug) from exc

    def profile_for(self, slug: str) -> str:
        """The profile this jurisdiction is canonicalized under.

        Falls back to the nearest ancestor that names one, which is how a city
        inherits its state's drafting conventions without repeating them.
        """

        for step in self.ancestry(slug):
            named = self._profiles.get(step)
            if named is not None and named != "default":
                return named
        return "default"

    def ancestry(self, slug: str) -> list[str]:
        """The slug, then each parent upward."""

        return self.get(slug).ancestry(self._by_slug)

    def children_of(self, slug: str) -> list[Jurisdiction]:
        return sorted(
            jurisdiction for jurisdiction in self._by_slug.values() if jurisdiction.parent == slug
        )

    def validate(self) -> None:
        """Reject dangling parents and cycles."""

        for jurisdiction in self._by_slug.values():
            if jurisdiction.parent is not None and jurisdiction.parent not in self._by_slug:
                raise ConfigError(
                    "jurisdiction names an unknown parent",
                    slug=jurisdiction.slug,
                    parent=jurisdiction.parent,
                )
            jurisdiction.ancestry(self._by_slug)


def _entry(raw: Any, position: int) -> tuple[Jurisdiction, str]:
    if not isinstance(raw, dict):
        raise ConfigError("jurisdiction entry must be a mapping", entry=position)
    missing = [key for key in ("slug", "name", "level") if key not in raw]
    if missing:
        raise ConfigError(
            "jurisdiction entry is incomplete", entry=position, missing=",".join(missing)
        )
    try:
        level = GovernmentLevel(str(raw["level"]))
    except ValueError as exc:
        raise ConfigError(
            "unknown government level", entry=position, level=str(raw["level"])
        ) from exc
    parent = raw.get("parent")
    jurisdiction = Jurisdiction(
        slug=str(raw["slug"]),
        name=str(raw["name"]),
        level=level,
        parent=None if parent is None else str(parent),
    )
    return jurisdiction, str(raw.get("profile", "default"))


def load_jurisdictions(path: str | Path) -> JurisdictionRegistry:
    """Load a registry from a YAML file."""

    target = Path(path)
    try:
        raw = yaml.safe_load(target.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ConfigError("jurisdiction table cannot be read", path=str(target)) from exc
    except yaml.YAMLError as exc:
        raise ConfigError("jurisdiction table is not valid YAML", path=str(target)) from exc
    if not isinstance(raw, dict) or not isinstance(raw.get("jurisdictions"), list):
        raise ConfigError("jurisdiction table needs a jurisdictions list", path=str(target))
    registry = JurisdictionRegistry()
    for position, item in enumerate(raw["jurisdictions"]):
        jurisdiction, profile = _entry(item, position)
        registry.add(jurisdiction, profile=profile)
    registry.validate()
    return registry

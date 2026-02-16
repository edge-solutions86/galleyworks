"""Deciding what needs reindexing.

Reindexing a corpus from scratch is minutes of work to discover that two files
changed. The store keeps a digest of each instrument's raw text, so a second run
can index only what moved.

The digest is of the raw text, not the fingerprints. Fingerprints depend on the
engine settings, and a settings change is already caught by the digest check on
open, which refuses the index outright rather than silently reindexing half of
it under one configuration and half under another.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass, field

from lexdrift.index.store import SqliteIndex, content_digest
from lexdrift.models import Instrument

__all__ = ["FreshnessReport", "content_digest", "plan_reindex", "record_digest"]


@dataclass(frozen=True, slots=True)
class FreshnessReport:
    """What a second run would have to do."""

    unchanged: tuple[str, ...] = field(default_factory=tuple)
    changed: tuple[str, ...] = field(default_factory=tuple)
    added: tuple[str, ...] = field(default_factory=tuple)
    removed: tuple[str, ...] = field(default_factory=tuple)

    @property
    def to_index(self) -> tuple[str, ...]:
        return tuple(sorted((*self.changed, *self.added)))

    @property
    def is_current(self) -> bool:
        return not (self.changed or self.added or self.removed)

    def summary(self) -> str:
        return (
            f"{len(self.unchanged)} unchanged, {len(self.changed)} changed, "
            f"{len(self.added)} new, {len(self.removed)} no longer present"
        )


def record_digest(store: SqliteIndex, instrument: Instrument) -> None:
    """Store the content digest for ``instrument``."""

    store.connection.execute(
        "UPDATE instrument SET content_digest = ? WHERE slug = ?",
        (content_digest(instrument), instrument.key.slug),
    )


def stored_digests(store: SqliteIndex) -> dict[str, str]:
    """Every recorded digest, by instrument slug."""

    rows = store.connection.execute(
        "SELECT slug, content_digest FROM instrument WHERE content_digest IS NOT NULL"
    ).fetchall()
    return {str(row["slug"]): str(row["content_digest"]) for row in rows}


def plan_reindex(store: SqliteIndex, instruments: Iterable[Instrument]) -> FreshnessReport:
    """Compare a corpus against what the index already holds."""

    known = stored_digests(store)
    unchanged: list[str] = []
    changed: list[str] = []
    added: list[str] = []
    seen: set[str] = set()
    for instrument in instruments:
        slug = instrument.key.slug
        seen.add(slug)
        recorded = known.get(slug)
        if recorded is None:
            added.append(slug)
        elif recorded == content_digest(instrument):
            unchanged.append(slug)
        else:
            changed.append(slug)
    removed = sorted(set(known) - seen)
    return FreshnessReport(
        unchanged=tuple(sorted(unchanged)),
        changed=tuple(sorted(changed)),
        added=tuple(sorted(added)),
        removed=tuple(removed),
    )

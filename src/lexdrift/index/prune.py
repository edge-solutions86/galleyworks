"""Removing rows that cannot contribute to an answer.

Two kinds accumulate. A segment with no winnowed fingerprints can never appear
in a candidate list, so it costs storage and scan time and returns nothing; it
happens when a subsection is a caption or a cross-reference and nothing else. An
instrument row with no segments left is what a ``remove_instrument`` on an older
schema left behind.

Pruning is separate from compaction because it removes information. Compaction
rebuilds derived tables and is always safe; this drops rows, so it is opt in and
it reports what it took.
"""

from __future__ import annotations

from dataclasses import dataclass

from lexdrift.fingerprint.codec import decode_record
from lexdrift.index.store import SqliteIndex
from lexdrift.util.logging import get_logger

__all__ = ["PruneReport", "find_prunable", "prune"]

_log = get_logger("index.prune")


@dataclass(frozen=True, slots=True)
class PruneReport:
    """What pruning removed, or would remove."""

    empty_segments: tuple[str, ...] = ()
    empty_instruments: tuple[str, ...] = ()

    @property
    def total(self) -> int:
        return len(self.empty_segments) + len(self.empty_instruments)

    @property
    def is_clean(self) -> bool:
        return self.total == 0

    def summary(self) -> str:
        if self.is_clean:
            return "nothing to prune"
        return (
            f"{len(self.empty_segments)} segments with no fingerprints, "
            f"{len(self.empty_instruments)} instruments with no segments"
        )


def find_prunable(store: SqliteIndex) -> PruneReport:
    """Report what pruning would remove, without removing it."""

    segments: list[str] = []
    for row in store.connection.execute("SELECT slug, segment_index, blob FROM segment").fetchall():
        record = decode_record(row["blob"])
        if not record.selected:
            segments.append(f"{row['slug']}#{row['segment_index']}")
    instruments = [
        str(row["slug"])
        for row in store.connection.execute(
            "SELECT slug FROM instrument WHERE slug NOT IN (SELECT DISTINCT slug FROM segment)"
        ).fetchall()
    ]
    return PruneReport(
        empty_segments=tuple(sorted(segments)),
        empty_instruments=tuple(sorted(instruments)),
    )


def prune(store: SqliteIndex) -> PruneReport:
    """Remove every row that cannot contribute to an answer."""

    report = find_prunable(store)
    if report.is_clean:
        return report
    connection = store.connection
    connection.execute("BEGIN")
    for slug in report.empty_segments:
        instrument, _, index = slug.rpartition("#")
        for table in ("band", "posting", "segment"):
            connection.execute(
                f"DELETE FROM {table} WHERE slug = ? AND segment_index = ?",
                (instrument, int(index)),
            )
    connection.executemany(
        "DELETE FROM instrument WHERE slug = ?",
        [(slug,) for slug in report.empty_instruments],
    )
    connection.execute("COMMIT")
    _log.info("index pruned", extra={"removed": report.total})
    return report

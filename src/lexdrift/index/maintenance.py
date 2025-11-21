"""Checking and compacting a sqlite index.

The band and posting tables are derived data. If a write is interrupted between
deleting a segment and deleting its buckets, they disagree with the segment
table, and the symptom is a candidate reference that cannot be dereferenced
during a query, hours later, in a stack trace that names neither the interrupted
write nor the row.

:func:`check_integrity` looks for that disagreement in both directions, and
:func:`compact` repairs it by rebuilding the derived tables from the stored
records, which are the only thing that cannot be recomputed.
"""

from __future__ import annotations

from dataclasses import dataclass

from lexdrift.fingerprint.codec import decode_record
from lexdrift.index.lsh import band_keys
from lexdrift.index.store import SqliteIndex
from lexdrift.util.bitops import to_signed64
from lexdrift.util.logging import get_logger

__all__ = ["CompactionReport", "IntegrityReport", "check_integrity", "compact"]

_log = get_logger("index.maintenance")


@dataclass(frozen=True, slots=True)
class IntegrityReport:
    """What a consistency check found."""

    segments: int
    orphan_bands: int
    orphan_postings: int
    missing_bands: int
    missing_postings: int
    unreadable: int

    @property
    def is_clean(self) -> bool:
        return not any(
            (
                self.orphan_bands,
                self.orphan_postings,
                self.missing_bands,
                self.missing_postings,
                self.unreadable,
            )
        )

    def summary(self) -> str:
        if self.is_clean:
            return f"{self.segments} segments, no problems found"
        return (
            f"{self.segments} segments, {self.orphan_bands} orphan bands, "
            f"{self.orphan_postings} orphan postings, {self.missing_bands} missing bands, "
            f"{self.missing_postings} missing postings, {self.unreadable} unreadable records"
        )


@dataclass(frozen=True, slots=True)
class CompactionReport:
    """What a compaction changed."""

    removed_bands: int
    removed_postings: int
    rebuilt_segments: int
    bytes_before: int
    bytes_after: int

    @property
    def bytes_reclaimed(self) -> int:
        return max(0, self.bytes_before - self.bytes_after)


def _connection(store: SqliteIndex) -> object:
    return store._connection  # noqa: SLF001


def check_integrity(store: SqliteIndex) -> IntegrityReport:
    """Compare the derived tables against the stored records."""

    conn = store._connection  # noqa: SLF001
    segments = int(conn.execute("SELECT COUNT(*) AS n FROM segment").fetchone()["n"])
    orphan_bands = int(
        conn.execute(
            "SELECT COUNT(*) AS n FROM band b LEFT JOIN segment s "
            "ON b.slug = s.slug AND b.segment_index = s.segment_index "
            "WHERE s.slug IS NULL"
        ).fetchone()["n"]
    )
    orphan_postings = int(
        conn.execute(
            "SELECT COUNT(*) AS n FROM posting p LEFT JOIN segment s "
            "ON p.slug = s.slug AND p.segment_index = s.segment_index "
            "WHERE s.slug IS NULL"
        ).fetchone()["n"]
    )
    missing_bands = 0
    missing_postings = 0
    unreadable = 0
    for row in conn.execute("SELECT slug, segment_index, blob FROM segment").fetchall():
        try:
            record = decode_record(row["blob"])
        except Exception:  # noqa: BLE001 - any decode failure is the same finding
            unreadable += 1
            continue
        expected_bands = set(band_keys(record.signature, store.config.lsh))
        found_bands = {
            bytes(item["band_key"])
            for item in conn.execute(
                "SELECT band_key FROM band WHERE slug = ? AND segment_index = ?",
                (row["slug"], row["segment_index"]),
            )
        }
        missing_bands += len(expected_bands - found_bands)
        expected_values = {to_signed64(value) for value in record.fingerprint_values}
        found_values = {
            int(item["value"])
            for item in conn.execute(
                "SELECT value FROM posting WHERE slug = ? AND segment_index = ?",
                (row["slug"], row["segment_index"]),
            )
        }
        missing_postings += len(expected_values - found_values)
    return IntegrityReport(
        segments=segments,
        orphan_bands=orphan_bands,
        orphan_postings=orphan_postings,
        missing_bands=missing_bands,
        missing_postings=missing_postings,
        unreadable=unreadable,
    )


def compact(store: SqliteIndex) -> CompactionReport:
    """Rebuild the derived tables from the records, then reclaim the space."""

    conn = store._connection  # noqa: SLF001
    before = int(conn.execute("PRAGMA page_count").fetchone()[0]) * int(
        conn.execute("PRAGMA page_size").fetchone()[0]
    )
    removed_bands = int(conn.execute("SELECT COUNT(*) AS n FROM band").fetchone()["n"])
    removed_postings = int(conn.execute("SELECT COUNT(*) AS n FROM posting").fetchone()["n"])
    rebuilt = 0
    conn.execute("BEGIN")
    conn.execute("DELETE FROM band")
    conn.execute("DELETE FROM posting")
    for row in conn.execute("SELECT slug, segment_index, blob FROM segment").fetchall():
        record = decode_record(row["blob"])
        conn.executemany(
            "INSERT OR IGNORE INTO band (band_key, slug, segment_index) VALUES (?, ?, ?)",
            [
                (key, row["slug"], row["segment_index"])
                for key in band_keys(record.signature, store.config.lsh)
            ],
        )
        conn.executemany(
            "INSERT OR IGNORE INTO posting (value, slug, segment_index) VALUES (?, ?, ?)",
            [
                (to_signed64(value), row["slug"], row["segment_index"])
                for value in record.fingerprint_values
            ],
        )
        rebuilt += 1
    conn.execute("COMMIT")
    conn.execute("VACUUM")
    after = int(conn.execute("PRAGMA page_count").fetchone()[0]) * int(
        conn.execute("PRAGMA page_size").fetchone()[0]
    )
    _log.info("index compacted", extra={"segments": rebuilt, "before": before, "after": after})
    return CompactionReport(
        removed_bands=removed_bands,
        removed_postings=removed_postings,
        rebuilt_segments=rebuilt,
        bytes_before=before,
        bytes_after=after,
    )

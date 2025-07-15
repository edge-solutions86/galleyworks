"""The sqlite-backed index.

Same operations as :class:`~lexdrift.index.memory.MemoryIndex`, against a file.
The band and posting tables are the on-disk form of the two dictionaries the
memory index keeps, and both are rewritten whenever a segment is replaced, so an
updated instrument never leaves stale buckets pointing at it.

The engine digest is written on the first insert and checked on every open. Two
indexes built with different shingle widths hold incomparable fingerprints, and
merging them silently is the failure this guards against.
"""

from __future__ import annotations

import sqlite3
from collections.abc import Iterable, Iterator
from pathlib import Path
from types import TracebackType

from lexdrift.config import EngineConfig
from lexdrift.errors import StoreError
from lexdrift.fingerprint.codec import decode_record, encode_record
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.identity import InstrumentKey
from lexdrift.index.lsh import band_keys
from lexdrift.index.memory import IndexStats, SegmentRef
from lexdrift.index.schema import connect, migrate, read_meta, write_meta
from lexdrift.models import Instrument
from lexdrift.util.bitops import to_signed64

__all__ = ["SqliteIndex"]

_DIGEST_KEY = "engine_digest"


class SqliteIndex:
    """A durable index over segment fingerprints."""

    def __init__(self, path: str | Path, config: EngineConfig | None = None) -> None:
        self.config = config or EngineConfig()
        self.path = str(path)
        self._connection = connect(self.path)
        migrate(self._connection)
        self._check_digest()

    def __enter__(self) -> SqliteIndex:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        self.close()

    def close(self) -> None:
        self._connection.close()

    @property
    def digest(self) -> str:
        return self.config.digest()

    def _check_digest(self) -> None:
        recorded = read_meta(self._connection, _DIGEST_KEY)
        if recorded is None:
            write_meta(self._connection, _DIGEST_KEY, self.digest)
            return
        if recorded != self.digest:
            raise StoreError(
                "index was built with a different engine configuration",
                found=recorded,
                expected=self.digest,
                path=self.path,
            )

    def __len__(self) -> int:
        row = self._connection.execute("SELECT COUNT(*) AS n FROM segment").fetchone()
        return int(row["n"])

    def __iter__(self) -> Iterator[SegmentFingerprint]:
        rows = self._connection.execute("SELECT blob FROM segment ORDER BY slug, segment_index")
        for row in rows:
            yield decode_record(row["blob"])

    def register(self, instrument: Instrument) -> None:
        """Record an instrument's identity so its segments can hang off it."""

        key = instrument.key
        self._connection.execute(
            "INSERT INTO instrument "
            "(slug, jurisdiction, session, chamber, number, title, enacted_on, source_format) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
            "ON CONFLICT(slug) DO UPDATE SET title = excluded.title, "
            "enacted_on = excluded.enacted_on, source_format = excluded.source_format",
            (
                key.slug,
                key.jurisdiction,
                key.session,
                key.chamber.value,
                key.number,
                instrument.title,
                None
                if instrument.effective_date is None
                else instrument.effective_date.isoformat(),
                instrument.source_format,
            ),
        )

    def add(self, record: SegmentFingerprint) -> SegmentRef:
        """Write one record, replacing any earlier record for that segment."""

        if len(record.signature) != self.config.lsh.permutations:
            raise StoreError(
                "record signature width does not match the index",
                width=len(record.signature),
                expected=self.config.lsh.permutations,
            )
        ref = SegmentRef.of(record)
        slug = record.key.slug
        try:
            self._connection.execute("BEGIN")
            self._ensure_instrument(record.key)
            self._delete_segment(slug, record.segment_index)
            self._connection.execute(
                "INSERT INTO segment "
                "(slug, segment_index, path, token_count, simhash, blob) "
                "VALUES (?, ?, ?, ?, ?, ?)",
                (
                    slug,
                    record.segment_index,
                    record.path,
                    record.token_count,
                    to_signed64(record.simhash),
                    encode_record(record),
                ),
            )
            self._connection.executemany(
                "INSERT OR IGNORE INTO band (band_key, slug, segment_index) VALUES (?, ?, ?)",
                [
                    (key, slug, record.segment_index)
                    for key in band_keys(record.signature, self.config.lsh)
                ],
            )
            self._connection.executemany(
                "INSERT OR IGNORE INTO posting (value, slug, segment_index) VALUES (?, ?, ?)",
                [
                    (to_signed64(value), slug, record.segment_index)
                    for value in record.fingerprint_values
                ],
            )
            self._connection.execute("COMMIT")
        except sqlite3.Error as exc:
            self._connection.execute("ROLLBACK")
            raise StoreError("index write failed", ref=ref.slug) from exc
        return ref

    def add_all(self, records: Iterable[SegmentFingerprint]) -> int:
        written = 0
        for record in records:
            self.add(record)
            written += 1
        return written

    def _ensure_instrument(self, key: InstrumentKey) -> None:
        self._connection.execute(
            "INSERT OR IGNORE INTO instrument "
            "(slug, jurisdiction, session, chamber, number) VALUES (?, ?, ?, ?, ?)",
            (key.slug, key.jurisdiction, key.session, key.chamber.value, key.number),
        )

    def _delete_segment(self, slug: str, segment_index: int) -> None:
        for table in ("band", "posting", "segment"):
            self._connection.execute(
                f"DELETE FROM {table} WHERE slug = ? AND segment_index = ?",
                (slug, segment_index),
            )

    def remove_instrument(self, key: InstrumentKey) -> int:
        """Drop every segment of one instrument, returning how many went."""

        row = self._connection.execute(
            "SELECT COUNT(*) AS n FROM segment WHERE slug = ?", (key.slug,)
        ).fetchone()
        count = int(row["n"])
        self._connection.execute("DELETE FROM band WHERE slug = ?", (key.slug,))
        self._connection.execute("DELETE FROM posting WHERE slug = ?", (key.slug,))
        self._connection.execute("DELETE FROM instrument WHERE slug = ?", (key.slug,))
        return count

    def record(self, ref: SegmentRef) -> SegmentFingerprint:
        row = self._connection.execute(
            "SELECT blob FROM segment WHERE slug = ? AND segment_index = ?",
            (ref.key.slug, ref.segment_index),
        ).fetchone()
        if row is None:
            raise StoreError("no such segment in the index", ref=ref.slug)
        return decode_record(row["blob"])

    def candidates(self, record: SegmentFingerprint) -> list[SegmentRef]:
        keys = band_keys(record.signature, self.config.lsh)
        placeholders = ",".join("?" for _ in keys)
        rows = self._connection.execute(
            f"SELECT DISTINCT slug, segment_index FROM band WHERE band_key IN ({placeholders})",
            keys,
        ).fetchall()
        own = SegmentRef.of(record)
        found = {_ref(row) for row in rows}
        found.discard(own)
        return sorted(found)

    def postings(self, value: int) -> list[SegmentRef]:
        rows = self._connection.execute(
            "SELECT slug, segment_index FROM posting WHERE value = ?", (to_signed64(value),)
        ).fetchall()
        return sorted(_ref(row) for row in rows)

    def document_frequency(self, value: int) -> int:
        row = self._connection.execute(
            "SELECT COUNT(*) AS n FROM posting WHERE value = ?", (to_signed64(value),)
        ).fetchone()
        return int(row["n"])

    def instruments(self) -> list[InstrumentKey]:
        rows = self._connection.execute(
            "SELECT DISTINCT slug FROM segment ORDER BY slug"
        ).fetchall()
        return [InstrumentKey.parse(str(row["slug"])) for row in rows]

    def stats(self) -> IndexStats:
        def count(sql: str) -> int:
            return int(self._connection.execute(sql).fetchone()["n"])

        return IndexStats(
            instruments=len(self.instruments()),
            segments=count("SELECT COUNT(*) AS n FROM segment"),
            buckets=count("SELECT COUNT(DISTINCT band_key) AS n FROM band"),
            postings=count("SELECT COUNT(DISTINCT value) AS n FROM posting"),
            digest=self.digest,
        )


def _ref(row: sqlite3.Row) -> SegmentRef:
    return SegmentRef(
        key=InstrumentKey.parse(str(row["slug"])), segment_index=int(row["segment_index"])
    )

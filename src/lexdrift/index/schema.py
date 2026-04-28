"""The sqlite schema and its migrations.

Migrations are a plain list of statements keyed by the version they produce.
Opening a database runs whatever is missing inside one transaction, so an
interrupted upgrade leaves the file at the version it started from rather than
somewhere in between.

The engine digest is recorded in ``meta`` on first write. An index built with
one shingle width cannot be queried with another, and a mismatch is refused on
open instead of quietly returning wrong answers.
"""

from __future__ import annotations

import sqlite3
from pathlib import Path

from lexdrift.errors import StoreError

__all__ = [
    "MIGRATIONS",
    "SCHEMA_VERSION",
    "checkpoint",
    "connect",
    "migrate",
    "read_meta",
    "user_version",
    "write_meta",
]

SCHEMA_VERSION = 4

MIGRATIONS: dict[int, tuple[str, ...]] = {
    1: (
        """
        CREATE TABLE meta (
            name  TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE instrument (
            slug         TEXT PRIMARY KEY,
            jurisdiction TEXT NOT NULL,
            session      TEXT NOT NULL,
            chamber      TEXT NOT NULL,
            number       TEXT NOT NULL,
            title        TEXT NOT NULL DEFAULT '',
            enacted_on   TEXT
        )
        """,
        """
        CREATE TABLE segment (
            slug          TEXT NOT NULL,
            segment_index INTEGER NOT NULL,
            path          TEXT NOT NULL,
            token_count   INTEGER NOT NULL,
            simhash       INTEGER NOT NULL,
            blob          BLOB NOT NULL,
            PRIMARY KEY (slug, segment_index),
            FOREIGN KEY (slug) REFERENCES instrument(slug) ON DELETE CASCADE
        )
        """,
    ),
    2: (
        """
        CREATE TABLE band (
            band_key      BLOB NOT NULL,
            slug          TEXT NOT NULL,
            segment_index INTEGER NOT NULL,
            PRIMARY KEY (band_key, slug, segment_index)
        )
        """,
        "CREATE INDEX band_by_segment ON band (slug, segment_index)",
        """
        CREATE TABLE posting (
            value         INTEGER NOT NULL,
            slug          TEXT NOT NULL,
            segment_index INTEGER NOT NULL,
            PRIMARY KEY (value, slug, segment_index)
        )
        """,
        "CREATE INDEX posting_by_segment ON posting (slug, segment_index)",
    ),
    3: (
        "ALTER TABLE instrument ADD COLUMN source_format TEXT NOT NULL DEFAULT 'unknown'",
        "CREATE INDEX instrument_by_jurisdiction ON instrument (jurisdiction, session)",
    ),
    4: ("ALTER TABLE instrument ADD COLUMN content_digest TEXT",),
}

# Tuned for the two shapes of work this index sees: one long write during a
# corpus load, and many small reads afterwards.
#
# * WAL so a reader is never blocked by the writer
# * NORMAL sync because the index is derived data; a lost transaction after a
#   crash costs a reindex, not a document
# * a 64 MiB page cache, which holds the band table of a mid sized corpus
# * memory temp storage so the sorts a query does never touch the disk
_PRAGMAS = (
    "PRAGMA journal_mode = WAL",
    "PRAGMA synchronous = NORMAL",
    "PRAGMA foreign_keys = ON",
    "PRAGMA busy_timeout = 5000",
    "PRAGMA cache_size = -65536",
    "PRAGMA temp_store = MEMORY",
)

_MEMORY_ONLY_SKIPS = ("PRAGMA journal_mode",)


def checkpoint(connection: sqlite3.Connection) -> int:
    """Fold the write ahead log back into the database file.

    Without this a long lived writer leaves a WAL beside the index that can
    outgrow it, and a reader opening the file cold pays to replay the whole log.
    """

    try:
        row = connection.execute("PRAGMA wal_checkpoint(TRUNCATE)").fetchone()
    except sqlite3.Error:
        return 0
    if row is None:
        return 0
    # A database with no write ahead log answers (1, -1, -1): busy, no frames.
    # That is not a failure, it is a database that had nothing to fold back.
    pages = int(row[1])
    return max(0, pages)


def connect(path: str | Path) -> sqlite3.Connection:
    """Open ``path`` with the pragmas the store depends on."""

    target = str(path)
    try:
        # The service answers requests from a thread pool, so the connection has
        # to outlive the thread that opened it. sqlite's own locking plus the
        # busy timeout below is what serializes access; python's thread check
        # would only turn a concurrent read into a crash.
        connection = sqlite3.connect(target, isolation_level=None, check_same_thread=False)
    except sqlite3.Error as exc:
        raise StoreError("index database cannot be opened", path=target) from exc
    connection.row_factory = sqlite3.Row
    for pragma in _PRAGMAS:
        if target == ":memory:" and pragma.startswith(_MEMORY_ONLY_SKIPS):
            continue
        connection.execute(pragma)
    return connection


def user_version(connection: sqlite3.Connection) -> int:
    row = connection.execute("PRAGMA user_version").fetchone()
    return int(row[0])


def migrate(connection: sqlite3.Connection) -> int:
    """Bring the database up to :data:`SCHEMA_VERSION`, returning the version."""

    current = user_version(connection)
    if current > SCHEMA_VERSION:
        raise StoreError(
            "index was written by a newer schema",
            found=current,
            supported=SCHEMA_VERSION,
        )
    if current == SCHEMA_VERSION:
        return current
    try:
        connection.execute("BEGIN")
        for version in range(current + 1, SCHEMA_VERSION + 1):
            for statement in MIGRATIONS[version]:
                connection.execute(statement)
        connection.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
        connection.execute("COMMIT")
    except sqlite3.Error as exc:
        connection.execute("ROLLBACK")
        raise StoreError("index migration failed", from_version=current) from exc
    return SCHEMA_VERSION


def read_meta(connection: sqlite3.Connection, name: str) -> str | None:
    row = connection.execute("SELECT value FROM meta WHERE name = ?", (name,)).fetchone()
    return None if row is None else str(row["value"])


def write_meta(connection: sqlite3.Connection, name: str, value: str) -> None:
    connection.execute(
        "INSERT INTO meta (name, value) VALUES (?, ?) "
        "ON CONFLICT(name) DO UPDATE SET value = excluded.value",
        (name, value),
    )

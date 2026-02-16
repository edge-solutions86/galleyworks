from __future__ import annotations

import sqlite3
from collections.abc import Iterator
from pathlib import Path

import pytest

from lexdrift.errors import StoreError
from lexdrift.index.schema import (
    MIGRATIONS,
    SCHEMA_VERSION,
    connect,
    migrate,
    read_meta,
    user_version,
    write_meta,
)


@pytest.fixture
def connection() -> Iterator[sqlite3.Connection]:
    conn = connect(":memory:")
    try:
        yield conn
    finally:
        conn.close()


def tables(conn: sqlite3.Connection) -> set[str]:
    rows = conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'").fetchall()
    return {str(row["name"]) for row in rows}


def test_a_fresh_database_starts_at_version_zero(connection: sqlite3.Connection) -> None:
    assert user_version(connection) == 0


def test_migration_creates_every_table(connection: sqlite3.Connection) -> None:
    assert migrate(connection) == SCHEMA_VERSION
    assert {"meta", "instrument", "segment", "band", "posting"} <= tables(connection)


def test_migration_is_idempotent(connection: sqlite3.Connection) -> None:
    migrate(connection)
    assert migrate(connection) == SCHEMA_VERSION
    assert user_version(connection) == SCHEMA_VERSION


def test_migrations_are_numbered_consecutively() -> None:
    assert sorted(MIGRATIONS) == list(range(1, SCHEMA_VERSION + 1))


def test_a_newer_schema_is_refused(connection: sqlite3.Connection) -> None:
    connection.execute(f"PRAGMA user_version = {SCHEMA_VERSION + 1}")
    with pytest.raises(StoreError, match="newer schema") as caught:
        migrate(connection)
    assert caught.value.context["supported"] == SCHEMA_VERSION


def test_a_partial_upgrade_rolls_back(connection: sqlite3.Connection) -> None:
    migrate(connection)
    connection.execute("DROP TABLE band")
    connection.execute("PRAGMA user_version = 1")
    connection.execute("CREATE TABLE band (x INTEGER)")
    with pytest.raises(StoreError, match="migration failed"):
        migrate(connection)
    assert user_version(connection) == 1


def test_meta_round_trips(connection: sqlite3.Connection) -> None:
    migrate(connection)
    assert read_meta(connection, "digest") is None
    write_meta(connection, "digest", "abc123")
    assert read_meta(connection, "digest") == "abc123"


def test_meta_writes_are_upserts(connection: sqlite3.Connection) -> None:
    migrate(connection)
    write_meta(connection, "digest", "one")
    write_meta(connection, "digest", "two")
    assert read_meta(connection, "digest") == "two"


def test_the_later_columns_exist(connection: sqlite3.Connection) -> None:
    migrate(connection)
    columns = {row["name"] for row in connection.execute("PRAGMA table_info(instrument)")}
    assert "source_format" in columns
    assert "content_digest" in columns


def test_foreign_keys_cascade(connection: sqlite3.Connection) -> None:
    migrate(connection)
    connection.execute(
        "INSERT INTO instrument (slug, jurisdiction, session, chamber, number) "
        "VALUES ('wy/2025/lower/HB1', 'wy', '2025', 'lower', 'HB1')"
    )
    connection.execute(
        "INSERT INTO segment (slug, segment_index, path, token_count, simhash, blob) "
        "VALUES ('wy/2025/lower/HB1', 0, '1', 10, 0, X'00')"
    )
    connection.execute("DELETE FROM instrument WHERE slug = 'wy/2025/lower/HB1'")
    assert connection.execute("SELECT COUNT(*) FROM segment").fetchone()[0] == 0


def test_opening_a_file_database(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    conn = connect(path)
    try:
        migrate(conn)
        assert path.exists()
    finally:
        conn.close()


def test_opening_an_unreachable_path(tmp_path: Path) -> None:
    with pytest.raises(StoreError, match="cannot be opened"):
        connect(tmp_path / "missing" / "index.sqlite")

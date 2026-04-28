from __future__ import annotations

import sqlite3
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.index.schema import checkpoint, connect, migrate
from lexdrift.index.store import SqliteIndex


def settings(connection: sqlite3.Connection) -> dict[str, object]:
    return {
        name: connection.execute(f"PRAGMA {name}").fetchone()[0]
        for name in ("journal_mode", "synchronous", "foreign_keys", "cache_size", "temp_store")
    }


def test_a_file_database_uses_the_write_ahead_log(tmp_path: Path) -> None:
    connection = connect(tmp_path / "index.sqlite")
    try:
        assert settings(connection)["journal_mode"] == "wal"
    finally:
        connection.close()


def test_the_cache_and_temp_store_are_tuned(tmp_path: Path) -> None:
    connection = connect(tmp_path / "index.sqlite")
    try:
        values = settings(connection)
        assert values["cache_size"] == -65536
        assert values["temp_store"] == 2
        assert values["foreign_keys"] == 1
    finally:
        connection.close()


def test_a_memory_database_skips_only_the_journal_mode() -> None:
    connection = connect(":memory:")
    try:
        values = settings(connection)
        assert values["journal_mode"] == "memory"
        assert values["cache_size"] == -65536
    finally:
        connection.close()


def test_a_checkpoint_on_an_empty_database(tmp_path: Path) -> None:
    connection = connect(tmp_path / "index.sqlite")
    try:
        migrate(connection)
        assert checkpoint(connection) >= 0
    finally:
        connection.close()


def test_a_checkpoint_on_a_memory_database_is_harmless() -> None:
    connection = connect(":memory:")
    try:
        migrate(connection)
        assert checkpoint(connection) == 0
    finally:
        connection.close()


def test_closing_leaves_no_write_ahead_log(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    fingerprinter = Fingerprinter()
    with SqliteIndex(path) as store, store.bulk():
        for instrument in golden_instruments():
            for record in fingerprinter.fingerprint(instrument):
                store.add(record)
    wal = path.with_name(path.name + "-wal")
    assert not wal.exists() or wal.stat().st_size == 0


def test_the_index_reopens_after_a_checkpoint(tmp_path: Path) -> None:
    path = tmp_path / "index.sqlite"
    with SqliteIndex(path) as store:
        store.add_all(Fingerprinter().fingerprint(golden_instruments()[0]))
        expected = len(store)
    with SqliteIndex(path) as reopened:
        assert len(reopened) == expected


def test_a_closed_connection_does_not_raise(tmp_path: Path) -> None:
    store = SqliteIndex(tmp_path / "index.sqlite")
    store.close()
    with pytest.raises(sqlite3.ProgrammingError):
        store.connection.execute("SELECT 1")

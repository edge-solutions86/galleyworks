from __future__ import annotations

from dataclasses import replace
from pathlib import Path

import pytest

from golden_corpus import golden_instruments
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.index.freshness import content_digest, plan_reindex, record_digest
from lexdrift.index.store import SqliteIndex
from lexdrift.models import Instrument


@pytest.fixture
def store(tmp_path: Path) -> SqliteIndex:
    return SqliteIndex(tmp_path / "index.sqlite")


def load(store: SqliteIndex, instruments: list[Instrument]) -> None:
    fingerprinter = Fingerprinter()
    with store.bulk():
        for instrument in instruments:
            store.register(instrument)
            for record in fingerprinter.fingerprint(instrument):
                store.add(record)


def test_a_digest_is_stable() -> None:
    one = golden_instruments()[0]
    assert content_digest(one) == content_digest(golden_instruments()[0])
    assert len(content_digest(one)) == 16


def test_a_changed_text_changes_the_digest() -> None:
    one = golden_instruments()[0]
    other = replace(one, raw_text=one.raw_text + " ")
    assert content_digest(one) != content_digest(other)


def test_an_empty_index_needs_everything(store: SqliteIndex) -> None:
    report = plan_reindex(store, golden_instruments())
    assert len(report.added) == 4
    assert report.unchanged == ()
    assert not report.is_current
    assert len(report.to_index) == 4


def test_a_loaded_index_is_current(store: SqliteIndex) -> None:
    instruments = golden_instruments()
    load(store, instruments)
    report = plan_reindex(store, instruments)
    assert report.is_current
    assert len(report.unchanged) == 4
    assert report.to_index == ()


def test_a_changed_document_is_detected(store: SqliteIndex) -> None:
    instruments = golden_instruments()
    load(store, instruments)
    edited = list(instruments)
    edited[1] = replace(edited[1], raw_text=edited[1].raw_text.replace("sixty", "ninety"))
    report = plan_reindex(store, edited)
    assert report.changed == ("mt/2024/lower/HB0311",)
    assert report.to_index == ("mt/2024/lower/HB0311",)


def test_a_new_document_is_detected(store: SqliteIndex) -> None:
    instruments = golden_instruments()
    load(store, instruments[:3])
    report = plan_reindex(store, instruments)
    assert report.added == ("co/2025/upper/SB0007",)
    assert len(report.unchanged) == 3


def test_a_removed_document_is_detected(store: SqliteIndex) -> None:
    instruments = golden_instruments()
    load(store, instruments)
    report = plan_reindex(store, instruments[:3])
    assert report.removed == ("co/2025/upper/SB0007",)
    assert not report.is_current


def test_the_summary_reads_clearly(store: SqliteIndex) -> None:
    instruments = golden_instruments()
    load(store, instruments)
    assert "4 unchanged" in plan_reindex(store, instruments).summary()


def test_a_digest_can_be_recorded_after_the_fact(store: SqliteIndex) -> None:
    instrument = golden_instruments()[0]
    fingerprinter = Fingerprinter()
    store.add_all(fingerprinter.fingerprint(instrument))
    assert plan_reindex(store, [instrument]).added
    record_digest(store, instrument)
    assert plan_reindex(store, [instrument]).is_current


def test_reindexing_only_what_changed(store: SqliteIndex) -> None:
    instruments = golden_instruments()
    load(store, instruments)
    edited = list(instruments)
    edited[1] = replace(edited[1], raw_text=edited[1].raw_text.replace("sixty", "ninety"))
    plan = plan_reindex(store, edited)
    fingerprinter = Fingerprinter()
    for instrument in edited:
        if instrument.key.slug in plan.to_index:
            store.remove_instrument(instrument.key)
            store.register(instrument)
            store.add_all(fingerprinter.fingerprint(instrument))
    assert plan_reindex(store, edited).is_current
    assert len(store.instruments()) == 4

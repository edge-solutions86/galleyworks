from __future__ import annotations

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.config import EngineConfig, LshConfig
from lexdrift.errors import StoreError
from lexdrift.index.base import SegmentRef
from lexdrift.index.memory import MemoryIndex


def wyoming() -> list:
    return fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE))


def montana() -> list:
    return fingerprints(make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE))


def unrelated() -> list:
    return fingerprints(make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE))


def test_adding_records_grows_the_index() -> None:
    index = MemoryIndex()
    added = index.add_all(wyoming())
    assert added == len(index) > 0


def test_a_reference_names_its_segment() -> None:
    index = MemoryIndex()
    ref = index.add(wyoming()[0])
    assert ref.slug.startswith("wy/2025/lower/HB0142#")
    assert ref in index


def test_records_can_be_read_back() -> None:
    index = MemoryIndex()
    record = wyoming()[0]
    ref = index.add(record)
    assert index.record(ref) == record


def test_an_unknown_reference_is_an_error() -> None:
    index = MemoryIndex()
    ref = SegmentRef(key=wyoming()[0].key, segment_index=99)
    with pytest.raises(StoreError, match="no such segment"):
        index.record(ref)


def test_adding_the_same_segment_twice_replaces_it() -> None:
    index = MemoryIndex()
    record = wyoming()[0]
    index.add(record)
    index.add(record)
    assert len(index) == 1


def test_a_signature_of_the_wrong_width_is_rejected() -> None:
    index = MemoryIndex(EngineConfig(lsh=LshConfig(permutations=64, bands=16)))
    with pytest.raises(StoreError, match="does not match the index"):
        index.add(wyoming()[0])


def test_a_copied_clause_is_a_candidate() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    probe = next(r for r in montana() if r.path == "1(a)")
    assert index.candidates(probe)


def test_an_unrelated_clause_is_not_a_candidate() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    probe = next(r for r in unrelated() if r.path == "1(a)")
    assert index.candidates(probe) == []


def test_a_record_is_never_its_own_candidate() -> None:
    index = MemoryIndex()
    records = wyoming()
    index.add_all(records)
    own = SegmentRef.of(records[0])
    assert own not in index.candidates(records[0])


def test_postings_find_shared_fingerprints() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    probe = next(r for r in montana() if r.path == "1(a)")
    hits = [ref for value in probe.fingerprint_values for ref in index.postings(value)]
    assert hits


def test_document_frequency_counts_segments() -> None:
    index = MemoryIndex()
    records = wyoming()
    index.add_all(records)
    value = records[0].fingerprint_values[0]
    assert index.document_frequency(value) >= 1
    assert index.document_frequency(0) == 0


def test_removing_an_instrument_clears_its_postings() -> None:
    index = MemoryIndex()
    records = wyoming()
    index.add_all(records)
    value = records[0].fingerprint_values[0]
    removed = index.remove_instrument(records[0].key)
    assert removed == len(records)
    assert len(index) == 0
    assert index.postings(value) == []


def test_removing_an_absent_instrument_is_a_no_op() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    assert index.remove_instrument(unrelated()[0].key) == 0


def test_removing_one_instrument_leaves_the_other() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    index.add_all(montana())
    index.remove_instrument(wyoming()[0].key)
    assert index.instruments() == [montana()[0].key]


def test_stats_summarise_the_index() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    index.add_all(unrelated())
    stats = index.stats()
    assert stats.instruments == 2
    assert stats.segments == len(index)
    assert stats.buckets > 0
    assert stats.digest == index.digest


def test_iteration_yields_every_record() -> None:
    index = MemoryIndex()
    index.add_all(wyoming())
    assert len(list(index)) == len(index)

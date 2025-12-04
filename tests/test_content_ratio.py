from __future__ import annotations

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.config import EngineConfig, MatchConfig
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.fingerprint.simhash import simhash_distance
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher

RECITAL = (
    "Notwithstanding any other provision of law and subject to the provisions "
    "of this act, in and of the same, to and for such of them as may be within "
    "or without it, and not otherwise."
)


def record_for(body: str, path: str = "1(a)"):
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=body)
    return next(r for r in Fingerprinter().fingerprint(instrument) if r.path == path)


def test_a_normal_clause_has_content() -> None:
    record = record_for(MODEL_CLAUSE)
    assert record.content_ratio > 0.4
    assert not record.is_recital


def test_a_recital_is_mostly_function_words() -> None:
    record = record_for(RECITAL)
    assert record.content_ratio < 0.25
    assert record.is_recital


def test_the_ratio_is_recorded_on_every_segment() -> None:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    assert all(0.0 <= r.content_ratio <= 1.0 for r in Fingerprinter().fingerprint(instrument))


def test_weighting_separates_two_recital_heavy_segments() -> None:
    left = record_for(f"{RECITAL} The board shall publish a schedule of appropriations.")
    right = record_for(f"{RECITAL} The county shall keep a register of well permits.")
    assert simhash_distance(left.simhash, right.simhash) > 8


def test_a_recital_segment_is_skipped_by_the_matcher() -> None:
    # The enclosing section still carries the catchline and the amended
    # citation, so it has content; only the recital subsection is skipped.
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=RECITAL)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=RECITAL)
    matches = Matcher(index).match_instrument(probe)
    assert all(match.query_path != "1(a)" for match in matches)


def test_lowering_the_floor_lets_the_recital_through() -> None:
    config = EngineConfig(match=MatchConfig(min_content_ratio=0.0))
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=RECITAL)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=RECITAL)
    assert Matcher(index, config).match_instrument(probe)


def test_a_real_clause_still_matches() -> None:
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert Matcher(index).match_instrument(probe)


def test_raising_the_floor_rejects_everything() -> None:
    config = EngineConfig(match=MatchConfig(min_content_ratio=1.0))
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert Matcher(index, config).match_instrument(probe) == []


def test_the_floor_is_part_of_the_match_config() -> None:
    assert EngineConfig().match.min_content_ratio == 0.25
    assert (
        EngineConfig().digest() == EngineConfig(match=MatchConfig(min_content_ratio=0.9)).digest()
    )

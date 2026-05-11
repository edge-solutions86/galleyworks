from __future__ import annotations

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.config import EngineConfig, MatchConfig
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.index.base import SegmentRef
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher, containment_floor

REWORDED = MODEL_CLAUSE.replace("sixty\n(60) days", "ninety (90) days").replace(
    "the acreage irrigated under the appropriation",
    "the acreage under cultivation in the district",
)


def ref(jurisdiction: str, number: str = "HB0142") -> SegmentRef:
    return SegmentRef(InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number), 0)


def test_a_cross_jurisdiction_pair_uses_the_plain_floor() -> None:
    settings = MatchConfig(min_containment=0.35, same_jurisdiction_containment=0.6)
    assert containment_floor(ref("wy"), ref("mt"), settings) == 0.35


def test_a_same_jurisdiction_pair_uses_the_higher_floor() -> None:
    settings = MatchConfig(min_containment=0.35, same_jurisdiction_containment=0.6)
    assert containment_floor(ref("wy"), ref("wy", "HB0999"), settings) == 0.6


def test_the_higher_floor_never_lowers_the_bar() -> None:
    settings = MatchConfig(min_containment=0.8, same_jurisdiction_containment=0.2)
    assert containment_floor(ref("wy"), ref("wy", "HB0999"), settings) == 0.8


def test_the_default_is_higher_within_a_jurisdiction() -> None:
    settings = MatchConfig()
    assert settings.same_jurisdiction_containment > settings.min_containment


def indexed(jurisdiction: str, number: str, body: str) -> MemoryIndex:
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction=jurisdiction, number=number, body=body)))
    return index


def test_a_reworded_clause_still_crosses_a_state_line() -> None:
    index = indexed("wy", "HB0142", MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=REWORDED)
    assert Matcher(index).match_instrument(probe)


def test_the_floor_silences_a_partial_copy_inside_one_state() -> None:
    # The reworded clause scores about 0.75, which clears the cross border
    # floor and not this one.
    config = EngineConfig(match=MatchConfig(same_jurisdiction_containment=0.85))
    index = indexed("wy", "HB0142", MODEL_CLAUSE)
    probe = make_bill(jurisdiction="wy", number="HB0999", body=REWORDED)
    assert Matcher(index, config).match_instrument(probe) == []
    assert Matcher(index).match_instrument(probe)


def test_the_same_probe_still_crosses_a_border_at_that_floor() -> None:
    config = EngineConfig(match=MatchConfig(same_jurisdiction_containment=0.85))
    index = indexed("wy", "HB0142", MODEL_CLAUSE)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=REWORDED)
    assert Matcher(index, config).match_instrument(probe)


def test_a_wholesale_copy_is_reported_even_inside_one_state() -> None:
    index = indexed("wy", "HB0142", MODEL_CLAUSE)
    probe = make_bill(jurisdiction="wy", number="HB0999", body=MODEL_CLAUSE)
    assert Matcher(index).match_instrument(probe)


def test_the_floor_can_be_levelled() -> None:
    config = EngineConfig(match=MatchConfig(same_jurisdiction_containment=0.0))
    index = indexed("wy", "HB0142", MODEL_CLAUSE)
    probe = make_bill(jurisdiction="wy", number="HB0999", body=REWORDED)
    assert Matcher(index, config).match_instrument(probe)


def test_the_floor_is_not_part_of_the_index_digest() -> None:
    base = EngineConfig()
    other = EngineConfig(match=MatchConfig(same_jurisdiction_containment=0.9))
    assert base.digest() == other.digest()

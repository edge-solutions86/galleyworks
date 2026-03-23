from __future__ import annotations

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from golden_corpus import golden_instruments
from lexdrift.config import EngineConfig, MatchConfig
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.align import Anchor, Passage
from lexdrift.match.engine import Matcher, passage_is_reportable
from lexdrift.util.spans import TextSpan


def anchors(*values: int) -> tuple[Anchor, ...]:
    return tuple(
        Anchor(
            query_position=position,
            other_position=position,
            value=value,
            query=TextSpan(position * 10, position * 10 + 8),
            other=TextSpan(position * 10, position * 10 + 8),
        )
        for position, value in enumerate(values)
    )


def passage(length: int, *values: int) -> Passage:
    return Passage(query=TextSpan(0, length), other=TextSpan(0, length), anchors=anchors(*values))


def test_a_long_passage_on_distinct_anchors_is_reportable() -> None:
    settings = MatchConfig(min_passage_chars=90, min_distinct_anchors=2)
    assert passage_is_reportable(passage(200, 1, 2, 3), settings)


def test_a_short_passage_is_not() -> None:
    settings = MatchConfig(min_passage_chars=90, min_distinct_anchors=2)
    assert not passage_is_reportable(passage(50, 1, 2, 3), settings)


def test_a_long_passage_on_one_repeated_anchor_is_not() -> None:
    settings = MatchConfig(min_passage_chars=90, min_distinct_anchors=2)
    assert not passage_is_reportable(passage(500, 7, 7, 7, 7), settings)


def test_the_floor_can_be_lowered() -> None:
    settings = MatchConfig(min_passage_chars=90, min_distinct_anchors=1)
    assert passage_is_reportable(passage(500, 7, 7, 7), settings)


def test_the_default_floor_is_two() -> None:
    assert MatchConfig().min_distinct_anchors == 2


def test_a_match_reports_its_distinct_anchor_count() -> None:
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    match = Matcher(index).match_instrument(probe)[0]
    assert match.distinct_anchors >= 2


def test_a_real_copy_still_matches() -> None:
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert Matcher(index).match_instrument(probe)


def test_a_high_floor_rejects_everything() -> None:
    config = EngineConfig(match=MatchConfig(min_distinct_anchors=1000))
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert Matcher(index, config).match_instrument(probe) == []


def test_the_golden_family_survives_the_floor() -> None:
    from lexdrift.pipeline import Engine

    result = Engine().run_over(golden_instruments())
    assert result.matches
    assert all(match.distinct_anchors >= 2 for match in result.matches)


def test_the_floor_is_not_part_of_the_index_digest() -> None:
    base = EngineConfig()
    strict = EngineConfig(match=MatchConfig(min_distinct_anchors=9))
    assert base.digest() == strict.digest()

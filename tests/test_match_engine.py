from __future__ import annotations

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.config import EngineConfig, MatchConfig
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher

REWORDED = MODEL_CLAUSE.replace("sixty\n(60) days", "ninety (90) days").replace(
    "the acreage irrigated under the appropriation",
    "the acreage under cultivation in the district",
)


def indexed(*bodies: tuple[str, str, str], config: EngineConfig | None = None) -> MemoryIndex:
    index = MemoryIndex(config)
    for jurisdiction, number, body in bodies:
        index.add_all(fingerprints(make_bill(jurisdiction=jurisdiction, number=number, body=body)))
    return index


def test_a_copied_clause_is_matched() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matches = Matcher(index).match_instrument(probe)
    assert matches
    assert all(match.crosses_jurisdictions for match in matches)


def test_an_unrelated_bill_matches_nothing() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="co", number="SB0007", body=LOCAL_CLAUSE)
    assert Matcher(index).match_instrument(probe) == []


def test_a_match_reports_both_paths() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    match = Matcher(index).match_instrument(probe)[0]
    assert match.query_path
    assert match.other_path
    assert match.other.key.jurisdiction == "wy"


def test_a_match_carries_readable_passages() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    match = Matcher(index).match_instrument(probe)[0]
    assert match.passage_count >= 1
    assert match.longest_passage >= 90
    quoted = match.passages[0].query.slice_of(probe.raw_text)
    assert "board" in quoted or "schedule" in quoted


def test_a_reworded_clause_still_matches_with_a_lower_score() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    exact = Matcher(index).match_instrument(
        make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    )
    partial = Matcher(index).match_instrument(
        make_bill(jurisdiction="mt", number="HB0312", body=REWORDED)
    )
    assert partial
    assert max(m.score.best_containment for m in partial) < max(
        m.score.best_containment for m in exact
    )


def test_a_high_containment_floor_rejects_the_reworded_clause() -> None:
    config = EngineConfig(match=MatchConfig(min_containment=0.99))
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    matcher = Matcher(index, config)
    assert (
        matcher.match_instrument(make_bill(jurisdiction="mt", number="HB0312", body=REWORDED)) == []
    )


def test_a_long_passage_floor_rejects_short_overlaps() -> None:
    config = EngineConfig(match=MatchConfig(min_passage_chars=100000))
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    matcher = Matcher(index, config)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert matcher.match_instrument(probe) == []


def test_matches_are_ordered_by_score_by_default() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE), ("nd", "HB0044", REWORDED))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matches = Matcher(index).match_instrument(probe)
    scores = [match.score.best_containment for match in matches]
    assert scores == sorted(scores, reverse=True)


def test_matches_can_be_ordered_by_passage_length() -> None:
    config = EngineConfig(match=MatchConfig(order="length"))
    index = indexed(("wy", "HB0142", MODEL_CLAUSE), ("nd", "HB0044", REWORDED))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matches = Matcher(index, config).match_instrument(probe)
    lengths = [match.longest_passage for match in matches]
    assert lengths == sorted(lengths, reverse=True)


def test_the_candidate_cap_is_honoured() -> None:
    config = EngineConfig(match=MatchConfig(max_candidates=1))
    index = indexed(("wy", "HB0142", MODEL_CLAUSE), ("nd", "HB0044", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matcher = Matcher(index, config)
    for record in fingerprints(probe):
        assert len(matcher.match_record(record)) <= 1


def test_matching_records_directly_agrees_with_matching_the_instrument() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matcher = Matcher(index)
    by_instrument = matcher.match_instrument(probe)
    by_record = matcher.match_records(fingerprints(probe))
    assert [m.other for m in by_instrument] == [m.other for m in by_record]


def test_matching_against_an_empty_index() -> None:
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    assert Matcher(MemoryIndex()).match_instrument(probe) == []


def test_a_bill_matched_against_itself_finds_no_self_match() -> None:
    index = indexed(("wy", "HB0142", MODEL_CLAUSE))
    probe = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    matches = Matcher(index).match_instrument(probe)
    assert all(match.query != match.other for match in matches)

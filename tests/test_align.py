from __future__ import annotations

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.errors import MatchError
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.fingerprint.shingle import Shingle
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.match.align import Anchor, align, anchors_between, chain_anchors
from lexdrift.util.spans import TextSpan


def anchor(query_position: int, other_position: int) -> Anchor:
    return Anchor(
        query_position=query_position,
        other_position=other_position,
        value=query_position,
        query=TextSpan(query_position, query_position + 5),
        other=TextSpan(other_position, other_position + 5),
    )


def record(values: list[int], *, start: int = 0) -> SegmentFingerprint:
    return SegmentFingerprint(
        key=InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"),
        segment_index=0,
        path="1",
        token_count=100,
        signature=(),
        simhash=0,
        selected=tuple(
            Shingle(value=value, position=start + index, span=TextSpan(index * 10, index * 10 + 8))
            for index, value in enumerate(values)
        ),
        canonical="",
    )


def clause_records(body: str, jurisdiction: str, number: str) -> SegmentFingerprint:
    instrument = make_bill(jurisdiction=jurisdiction, number=number, body=body)
    return next(r for r in fingerprints(instrument) if r.path == "1(a)")


def test_shared_values_become_anchors() -> None:
    left = record([10, 20, 30, 40])
    right = record([99, 20, 30, 77])
    anchors = anchors_between(left, right)
    assert [a.value for a in anchors] == [20, 30]


def test_anchors_carry_both_spans() -> None:
    left = record([10, 20])
    right = record([20])
    found = anchors_between(left, right)[0]
    assert found.query.start == 10
    assert found.other.start == 0


def test_no_shared_values_gives_no_anchors() -> None:
    assert anchors_between(record([1, 2]), record([3, 4])) == []


def test_anchors_come_back_in_query_order() -> None:
    anchors = anchors_between(record([30, 10, 20]), record([10, 20, 30]))
    assert [a.query_position for a in anchors] == sorted(a.query_position for a in anchors)


def test_a_monotone_run_is_one_chain() -> None:
    chains = chain_anchors([anchor(0, 0), anchor(1, 1), anchor(2, 2)], max_gap=4)
    assert len(chains) == 1
    assert len(chains[0]) == 3


def test_a_backward_jump_starts_a_new_chain() -> None:
    chains = chain_anchors([anchor(0, 10), anchor(1, 11), anchor(2, 3)], max_gap=40)
    assert [len(chain) for chain in chains] == [2, 1]


def test_a_wide_gap_starts_a_new_chain() -> None:
    chains = chain_anchors([anchor(0, 0), anchor(100, 100)], max_gap=10)
    assert [len(chain) for chain in chains] == [1, 1]


def test_a_gap_exactly_at_the_limit_is_kept() -> None:
    chains = chain_anchors([anchor(0, 0), anchor(10, 10)], max_gap=10)
    assert len(chains) == 1


def test_chaining_an_empty_list() -> None:
    assert chain_anchors([], max_gap=4) == []


def test_a_negative_gap_is_rejected() -> None:
    with pytest.raises(MatchError, match="must not be negative"):
        chain_anchors([], max_gap=-1)


def test_align_needs_two_anchors_by_default() -> None:
    left = record([1, 2, 3])
    right = record([9, 2, 9])
    assert align(left, right) == []
    assert align(left, right, min_anchors=1)


def test_min_anchors_must_be_positive() -> None:
    with pytest.raises(MatchError, match="at least one"):
        align(record([1]), record([1]), min_anchors=0)


def test_a_copied_clause_aligns_as_one_passage() -> None:
    left = clause_records(MODEL_CLAUSE, "wy", "HB0142")
    right = clause_records(MODEL_CLAUSE, "mt", "HB0311")
    passages = align(left, right)
    assert passages
    assert passages[0].anchor_count >= 2
    assert passages[0].length > 0


def test_an_unrelated_clause_does_not_align() -> None:
    left = clause_records(MODEL_CLAUSE, "wy", "HB0142")
    right = clause_records(LOCAL_CLAUSE, "co", "SB0007")
    assert align(left, right) == []


def test_passages_come_back_longest_first() -> None:
    left = record([1, 2, 3, 50, 60, 61, 62, 63])
    right = record([1, 2, 3, 99, 60, 61, 62, 63])
    passages = align(left, right, max_gap=1)
    lengths = [passage.length for passage in passages]
    assert lengths == sorted(lengths, reverse=True)


def test_a_passage_reports_both_sides() -> None:
    left = clause_records(MODEL_CLAUSE, "wy", "HB0142")
    right = clause_records(MODEL_CLAUSE, "mt", "HB0311")
    passage = align(left, right)[0]
    assert passage.query_length > 0
    assert passage.other_length > 0
    assert passage.length == min(passage.query_length, passage.other_length)

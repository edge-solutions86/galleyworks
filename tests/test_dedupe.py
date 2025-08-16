from __future__ import annotations

import pytest

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.errors import MatchError
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.index.base import SegmentRef
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.align import Anchor, Passage
from lexdrift.match.dedupe import dedupe_matches, dedupe_passages, overlap_fraction
from lexdrift.match.engine import Matcher, SegmentMatch
from lexdrift.match.scoring import score_pair
from lexdrift.util.spans import TextSpan


def passage(qs: int, qe: int, os_: int, oe: int) -> Passage:
    anchor = Anchor(
        query_position=qs,
        other_position=os_,
        value=qs,
        query=TextSpan(qs, qe),
        other=TextSpan(os_, oe),
    )
    return Passage(query=TextSpan(qs, qe), other=TextSpan(os_, oe), anchors=(anchor,))


def match(query_index: int, other_index: int, passages: tuple[Passage, ...]) -> SegmentMatch:
    left = InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311")
    right = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")
    return SegmentMatch(
        query=SegmentRef(left, query_index),
        query_path=str(query_index),
        other=SegmentRef(right, other_index),
        other_path=str(other_index),
        score=score_pair([1, 2, 3], [1, 2, 3]),
        passages=passages,
    )


def test_overlap_of_identical_spans_is_one() -> None:
    assert overlap_fraction(TextSpan(0, 10), TextSpan(0, 10)) == 1.0


def test_overlap_uses_the_shorter_span() -> None:
    assert overlap_fraction(TextSpan(0, 100), TextSpan(10, 20)) == 1.0


def test_disjoint_spans_do_not_overlap() -> None:
    assert overlap_fraction(TextSpan(0, 10), TextSpan(20, 30)) == 0.0


def test_an_empty_span_does_not_overlap() -> None:
    assert overlap_fraction(TextSpan(5, 5), TextSpan(0, 10)) == 0.0


def test_a_contained_passage_is_dropped() -> None:
    outer = passage(0, 100, 0, 100)
    inner = passage(10, 60, 10, 60)
    assert dedupe_passages([inner, outer]) == [outer]


def test_disjoint_passages_are_both_kept() -> None:
    first = passage(0, 50, 0, 50)
    second = passage(200, 260, 200, 260)
    assert len(dedupe_passages([first, second])) == 2


def test_overlap_on_only_one_side_keeps_both() -> None:
    first = passage(0, 100, 0, 100)
    second = passage(10, 90, 500, 580)
    assert len(dedupe_passages([first, second])) == 2


def test_surviving_passages_come_back_in_query_order() -> None:
    kept = dedupe_passages([passage(300, 400, 300, 400), passage(0, 200, 0, 200)])
    assert [p.query.start for p in kept] == [0, 300]


def test_a_bad_overlap_threshold_is_rejected() -> None:
    with pytest.raises(MatchError, match=r"\(0, 1\]"):
        dedupe_passages([], min_overlap=0.0)


def test_a_duplicate_match_is_dropped() -> None:
    parent = match(0, 0, (passage(0, 200, 0, 200),))
    child = match(1, 1, (passage(20, 180, 20, 180),))
    assert dedupe_matches([child, parent]) == [parent]


def test_matches_on_different_instruments_are_both_kept() -> None:
    left = match(0, 0, (passage(0, 200, 0, 200),))
    right = SegmentMatch(
        query=left.query,
        query_path=left.query_path,
        other=SegmentRef(InstrumentKey("nd", "2025", Chamber.LOWER, "HB0044"), 0),
        other_path="0",
        score=left.score,
        passages=left.passages,
    )
    assert len(dedupe_matches([left, right])) == 2


def test_matches_covering_different_regions_are_both_kept() -> None:
    first = match(0, 0, (passage(0, 200, 0, 200),))
    second = match(2, 2, (passage(900, 1100, 900, 1100),))
    assert len(dedupe_matches([first, second])) == 2


def test_deduping_an_empty_list() -> None:
    assert dedupe_matches([]) == []
    assert dedupe_passages([]) == []


def test_real_matches_collapse_to_one_finding() -> None:
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matches = Matcher(index).match_instrument(probe)
    assert len(dedupe_matches(matches)) <= len(matches)
    assert dedupe_matches(matches)

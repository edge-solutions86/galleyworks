from __future__ import annotations

import pytest

from lexdrift.errors import MatchError
from lexdrift.match.scoring import (
    containment,
    jaccard,
    overlap_coefficient,
    score_pair,
)


def test_jaccard_endpoints() -> None:
    assert jaccard([1, 2, 3], [1, 2, 3]) == 1.0
    assert jaccard([1, 2], [3, 4]) == 0.0
    assert jaccard([], []) == 0.0


def test_jaccard_is_symmetric() -> None:
    assert jaccard([1, 2, 3], [2, 3, 4]) == jaccard([2, 3, 4], [1, 2, 3])


def test_jaccard_counts_the_union() -> None:
    assert jaccard([1, 2, 3, 4], [3, 4, 5, 6]) == pytest.approx(2 / 6)


def test_containment_is_asymmetric() -> None:
    small = [1, 2]
    large = [1, 2, 3, 4, 5, 6, 7, 8]
    assert containment(small, large) == 1.0
    assert containment(large, small) == pytest.approx(0.25)


def test_containment_of_an_empty_set_is_zero() -> None:
    assert containment([], [1, 2]) == 0.0


def test_overlap_uses_the_smaller_set() -> None:
    assert overlap_coefficient([1, 2], [1, 2, 3, 4]) == 1.0
    assert overlap_coefficient([1, 2], []) == 0.0


def test_duplicates_do_not_change_a_score() -> None:
    assert jaccard([1, 1, 2], [1, 2]) == 1.0
    assert containment([1, 1, 2, 2], [1, 2]) == 1.0


def test_score_pair_reports_every_number_once() -> None:
    score = score_pair([1, 2, 3, 4], [3, 4, 5])
    assert score.shared == 2
    assert score.query_size == 4
    assert score.other_size == 3
    assert score.jaccard == pytest.approx(2 / 5)
    assert score.query_containment == pytest.approx(0.5)
    assert score.other_containment == pytest.approx(2 / 3)


def test_score_pair_agrees_with_the_standalone_functions() -> None:
    left, right = [1, 2, 3, 4], [3, 4, 5]
    score = score_pair(left, right)
    assert score.jaccard == jaccard(left, right)
    assert score.query_containment == containment(left, right)
    assert score.other_containment == containment(right, left)


def test_the_best_containment_is_the_larger_side() -> None:
    score = score_pair([1, 2, 3, 4], [3, 4, 5])
    assert score.best_containment == score.other_containment


def test_direction_follows_containment() -> None:
    assert score_pair([1, 2], [1, 2, 3, 4]).direction == "query-into-other"
    assert score_pair([1, 2, 3, 4], [1, 2]).direction == "other-into-query"
    assert score_pair([1, 2], [1, 2]).direction == "even"


def test_a_disjoint_pair_is_empty() -> None:
    score = score_pair([1, 2], [3, 4])
    assert score.is_empty
    assert score.jaccard == 0.0
    assert score.direction == "even"


def test_scoring_an_empty_set_is_rejected() -> None:
    with pytest.raises(MatchError, match="empty fingerprint set"):
        score_pair([], [1])
    with pytest.raises(MatchError, match="empty fingerprint set"):
        score_pair([1], [])


def test_a_small_ordinance_inside_a_long_act() -> None:
    ordinance = list(range(10))
    act = list(range(200))
    score = score_pair(ordinance, act)
    assert score.query_containment == 1.0
    assert score.jaccard < 0.06

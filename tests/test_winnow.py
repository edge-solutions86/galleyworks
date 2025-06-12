from __future__ import annotations

import pytest
from hypothesis import given
from hypothesis import strategies as st

from lexdrift.canon.tokens import tokenize, words_only
from lexdrift.errors import MatchError
from lexdrift.fingerprint.shingle import Shingle, word_shingles
from lexdrift.fingerprint.winnow import expected_density, guarantee_length, winnow
from lexdrift.util.spans import TextSpan


def fake(values: list[int]) -> list[Shingle]:
    return [
        Shingle(value=value, position=index, span=TextSpan(index, index + 1))
        for index, value in enumerate(values)
    ]


def shingles_of(text: str, width: int = 3) -> list[Shingle]:
    return word_shingles(list(words_only(tokenize(text))), width)


def test_a_window_of_one_keeps_everything() -> None:
    shingles = fake([5, 3, 9])
    assert winnow(shingles, 1) == shingles


def test_the_minimum_of_each_window_is_selected() -> None:
    selected = winnow(fake([7, 2, 9, 4, 8]), 3)
    assert [s.value for s in selected] == [2, 4]


def test_a_tie_takes_the_rightmost_occurrence() -> None:
    selected = winnow(fake([1, 5, 1, 5]), 3)
    assert [s.position for s in selected] == [2]


def test_a_repeated_selection_is_recorded_once() -> None:
    # The window at (1, 2) picks position 1 again; it is not recorded twice.
    selected = winnow(fake([9, 1, 9, 9, 9]), 2)
    assert [s.position for s in selected] == [1, 3, 4]


def test_a_falling_sequence_selects_each_new_minimum() -> None:
    selected = winnow(fake([5, 4, 3, 2, 1]), 2)
    assert [s.value for s in selected] == [4, 3, 2, 1]


def test_an_empty_input_yields_nothing() -> None:
    assert winnow([], 4) == []


def test_a_window_wider_than_the_input_still_selects_one() -> None:
    selected = winnow(fake([7, 2, 9]), 10)
    assert [s.value for s in selected] == [2]


def test_a_zero_window_is_rejected() -> None:
    with pytest.raises(MatchError, match="at least one"):
        winnow(fake([1]), 0)


def test_expected_density_falls_with_the_window() -> None:
    assert expected_density(1) == 1.0
    assert expected_density(3) == pytest.approx(0.5)
    assert expected_density(9) == pytest.approx(0.2)
    with pytest.raises(MatchError):
        expected_density(0)


def test_guarantee_length_is_the_sum_less_one() -> None:
    assert guarantee_length(5, 4) == 8
    with pytest.raises(MatchError):
        guarantee_length(0, 4)


@given(st.lists(st.integers(min_value=0, max_value=50), min_size=1, max_size=40))
def test_selection_is_a_subsequence_of_the_input(values: list[int]) -> None:
    shingles = fake(values)
    selected = winnow(shingles, 4)
    positions = [s.position for s in selected]
    assert positions == sorted(set(positions))
    assert all(shingles[p] in selected for p in positions)


@given(st.lists(st.integers(min_value=0, max_value=1000), min_size=8, max_size=60))
def test_every_window_contributes_a_selection(values: list[int]) -> None:
    window = 4
    selected = {s.position for s in winnow(fake(values), window)}
    for start in range(len(values) - window + 1):
        assert selected & set(range(start, start + window))


def test_a_shared_run_survives_winnowing_in_both_documents() -> None:
    shared = "the board shall publish a schedule of appropriations not later than sixty days"
    left = winnow(shingles_of("first preamble text here " + shared), 4)
    right = winnow(shingles_of(shared + " and a different tail follows"), 4)
    assert {s.value for s in left} & {s.value for s in right}


def test_selection_is_deterministic() -> None:
    shingles = shingles_of("the board shall publish a schedule of appropriations")
    assert winnow(shingles, 3) == winnow(shingles, 3)

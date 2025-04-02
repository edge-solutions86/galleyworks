from __future__ import annotations

import pytest
from hypothesis import given
from hypothesis import strategies as st

from lexdrift.errors import ParseError
from lexdrift.ingest.labels import (
    alpha_successor,
    is_roman,
    roman_successor,
    roman_value,
    to_roman,
)


@pytest.mark.parametrize(
    ("label", "expected"),
    [("a", "b"), ("h", "i"), ("y", "z"), ("z", "aa"), ("aa", "bb"), ("zz", "aaa")],
)
def test_alpha_successor(label: str, expected: str) -> None:
    assert alpha_successor(label) == expected


def test_alpha_successor_is_case_insensitive() -> None:
    assert alpha_successor("H") == "i"


@pytest.mark.parametrize("label", ["", "a1", "ab", "!"])
def test_alpha_successor_rejects_non_sequences(label: str) -> None:
    with pytest.raises(ParseError):
        alpha_successor(label)


@pytest.mark.parametrize("label", ["i", "iv", "xlii", "MCM"])
def test_is_roman_accepts_roman_digits(label: str) -> None:
    assert is_roman(label)


@pytest.mark.parametrize("label", ["", "a", "iz", "12"])
def test_is_roman_rejects_the_rest(label: str) -> None:
    assert not is_roman(label)


@pytest.mark.parametrize(
    ("label", "value"),
    [("i", 1), ("ii", 2), ("iv", 4), ("ix", 9), ("xiv", 14), ("xl", 40), ("mcmxcix", 1999)],
)
def test_roman_value(label: str, value: int) -> None:
    assert roman_value(label) == value


def test_roman_value_rejects_non_canonical_forms() -> None:
    with pytest.raises(ParseError, match="canonical form"):
        roman_value("iiii")
    with pytest.raises(ParseError, match="canonical form"):
        roman_value("viiii")


def test_roman_value_rejects_letters() -> None:
    with pytest.raises(ParseError, match="not a roman label"):
        roman_value("a")


@pytest.mark.parametrize(
    ("value", "label"), [(1, "i"), (4, "iv"), (9, "ix"), (40, "xl"), (2025, "mmxxv")]
)
def test_to_roman(value: int, label: str) -> None:
    assert to_roman(value) == label


def test_to_roman_rejects_zero_and_negatives() -> None:
    with pytest.raises(ParseError, match="start at one"):
        to_roman(0)
    with pytest.raises(ParseError, match="start at one"):
        to_roman(-3)


@given(st.integers(min_value=1, max_value=3999))
def test_roman_round_trips(value: int) -> None:
    assert roman_value(to_roman(value)) == value


@pytest.mark.parametrize(
    ("label", "expected"), [("i", "ii"), ("iii", "iv"), ("iv", "v"), ("ix", "x")]
)
def test_roman_successor(label: str, expected: str) -> None:
    assert roman_successor(label) == expected


def test_the_two_alphabets_disagree_about_i() -> None:
    assert alpha_successor("h") == "i"
    assert roman_successor("i") == "ii"
    assert is_roman("i")
    assert alpha_successor("i") == "j"

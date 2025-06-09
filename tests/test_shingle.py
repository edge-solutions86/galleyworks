from __future__ import annotations

import pytest

from lexdrift.canon.tokens import tokenize, words_only
from lexdrift.config import ShingleConfig, ShingleMode
from lexdrift.errors import MatchError
from lexdrift.fingerprint.shingle import char_shingles, shingles_for, word_shingles

TEXT = "the board shall publish a schedule"


def words(text: str = TEXT) -> list:
    return list(words_only(tokenize(text)))


def test_word_shingles_count() -> None:
    assert len(word_shingles(words(), 3)) == 4
    assert len(word_shingles(words(), 6)) == 1


def test_a_window_wider_than_the_text_yields_nothing() -> None:
    assert word_shingles(words(), 7) == []
    assert char_shingles("abc", 4) == []


def test_word_shingle_spans_cover_their_window() -> None:
    shingles = word_shingles(words(), 2)
    assert shingles[0].span.slice_of(TEXT) == "the board"
    assert shingles[-1].span.slice_of(TEXT) == "a schedule"


def test_word_shingles_are_positioned_from_zero() -> None:
    assert [s.position for s in word_shingles(words(), 3)] == [0, 1, 2, 3]


def test_word_shingles_ignore_case() -> None:
    lower = [s.value for s in word_shingles(words(TEXT), 3)]
    upper = [s.value for s in word_shingles(words(TEXT.upper()), 3)]
    assert lower == upper


def test_word_shingles_are_order_sensitive() -> None:
    forward = {s.value for s in word_shingles(words("the board shall"), 3)}
    backward = {s.value for s in word_shingles(words("shall board the"), 3)}
    assert forward != backward


def test_punctuation_does_not_enter_a_word_shingle() -> None:
    with_punctuation = [s.value for s in word_shingles(words("the board, shall act"), 2)]
    without = [s.value for s in word_shingles(words("the board shall act"), 2)]
    assert with_punctuation == without


def test_a_shared_run_produces_shared_shingles() -> None:
    left = {s.value for s in word_shingles(words("the board shall publish a schedule"), 3)}
    right = {s.value for s in word_shingles(words("the commission shall publish a schedule"), 3)}
    assert len(left & right) == 2


def test_char_shingles_count_and_spans() -> None:
    shingles = char_shingles("abcdef", 3)
    assert len(shingles) == 4
    assert shingles[0].span.slice_of("abcdef") == "abc"


def test_char_shingles_take_an_offset() -> None:
    shingles = char_shingles("abcd", 2, offset=10)
    assert shingles[0].span.start == 10


def test_zero_width_is_rejected() -> None:
    with pytest.raises(MatchError, match="at least one"):
        word_shingles(words(), 0)
    with pytest.raises(MatchError, match="at least one"):
        char_shingles("abc", 0)


def test_shingles_for_follows_the_configured_mode() -> None:
    word = shingles_for(TEXT, ShingleConfig(width=3))
    char = shingles_for(TEXT, ShingleConfig(mode=ShingleMode.CHARACTER, width=3))
    assert len(word) == 4
    assert len(char) == len(TEXT) - 2


def test_shingles_for_passes_the_offset_through() -> None:
    shingles = shingles_for(TEXT, ShingleConfig(width=2), offset=100)
    assert shingles[0].span.start == 100


def test_shingles_sort_by_position_then_value() -> None:
    shingles = word_shingles(words(), 2)
    assert sorted(shingles) == shingles


def test_an_empty_text_has_no_shingles() -> None:
    assert shingles_for("", ShingleConfig(width=3)) == []
    assert shingles_for("", ShingleConfig(mode=ShingleMode.CHARACTER, width=3)) == []

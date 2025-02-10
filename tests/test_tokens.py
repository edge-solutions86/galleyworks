from __future__ import annotations

import pytest

from lexdrift.canon.tokens import TokenKind, tokenize, words_only


def kinds(text: str) -> list[TokenKind]:
    return [token.kind for token in tokenize(text)]


def texts(text: str) -> list[str]:
    return [token.text for token in tokenize(text)]


def test_spans_index_back_into_the_source() -> None:
    source = "The board shall publish."
    for token in tokenize(source):
        assert token.span.slice_of(source) == token.text


def test_offset_shifts_every_span() -> None:
    source = "board shall"
    shifted = tokenize(source, offset=100)
    assert [t.span.start for t in shifted] == [100, 106]


def test_negative_offset_is_rejected() -> None:
    with pytest.raises(ValueError, match="non-negative"):
        tokenize("x", offset=-1)


def test_hyphenated_words_stay_together() -> None:
    assert texts("sixty-five per centum") == ["sixty-five", "per", "centum"]


def test_apostrophes_stay_inside_the_word() -> None:
    assert texts("the governor's office") == ["the", "governor's", "office"]


def test_trailing_hyphen_is_punctuation() -> None:
    assert texts("water- rights") == ["water", "-", "rights"]


def test_section_sign_is_its_own_token() -> None:
    tokens = tokenize("§ 41-3-101")
    assert tokens[0].kind is TokenKind.SECTION_SIGN
    assert tokens[1].text == "41-3-101"


def test_repeated_section_signs_are_one_token() -> None:
    tokens = tokenize("§§ 1 and 2")
    assert tokens[0].text == "§§"


def test_statute_numbers_keep_their_internal_punctuation() -> None:
    assert texts("W.S. 41-3-101(a)(ii)") == [
        "W",
        ".",
        "S",
        ".",
        "41-3-101",
        "(",
        "a",
        ")",
        "(",
        "ii",
        ")",
    ]


def test_ordinals_are_numbers() -> None:
    assert kinds("1st") == [TokenKind.NUMBER]
    assert kinds("2,500.75") == [TokenKind.NUMBER]


def test_words_only_drops_punctuation_and_signs() -> None:
    tokens = tokenize("§ 3. The board, however, shall act.")
    kept = [token.text for token in words_only(tokens)]
    assert kept == ["3", "The", "board", "however", "shall", "act"]


def test_lowered_and_is_word_helpers() -> None:
    token = tokenize("Board")[0]
    assert token.is_word
    assert token.lowered == "board"


def test_empty_text_yields_nothing() -> None:
    assert tokenize("") == []
    assert tokenize("   \n\t ") == []


def test_unicode_letters_are_words() -> None:
    assert kinds("señor") == [TokenKind.WORD]

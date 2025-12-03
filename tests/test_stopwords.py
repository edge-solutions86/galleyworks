from __future__ import annotations

import pytest

from lexdrift.canon.stopwords import STOPWORDS, content_ratio, content_tokens, is_stopword
from lexdrift.canon.tokens import tokenize, words_only
from lexdrift.fingerprint.shingle import word_shingles

CLAUSE = "the board shall publish a schedule of appropriations"


def words(text: str) -> list[str]:
    return [token.lowered for token in words_only(tokenize(text))]


@pytest.mark.parametrize("token", ["the", "of", "shall", "may", "within"])
def test_function_words_are_stopwords(token: str) -> None:
    assert is_stopword(token)


@pytest.mark.parametrize("token", ["board", "schedule", "appropriation", "publish"])
def test_subject_matter_is_not(token: str) -> None:
    assert not is_stopword(token)


def test_case_does_not_matter() -> None:
    assert is_stopword("The")
    assert is_stopword("SHALL")


def test_the_list_is_lowercase_and_unique() -> None:
    assert all(word == word.lower() for word in STOPWORDS)
    assert len(STOPWORDS) == len(set(STOPWORDS))


def test_content_tokens_drops_the_function_words() -> None:
    assert list(content_tokens(words(CLAUSE))) == [
        "board",
        "publish",
        "schedule",
        "appropriations",
    ]


def test_content_tokens_on_an_empty_input() -> None:
    assert list(content_tokens([])) == []


def test_the_content_ratio_of_a_normal_clause() -> None:
    assert 0.3 < content_ratio(words(CLAUSE)) < 0.6


def test_a_recital_has_a_low_content_ratio() -> None:
    recital = "notwithstanding any other provision of law and subject to the provisions of this act"
    assert content_ratio(words(recital)) < content_ratio(words(CLAUSE))


def test_an_empty_token_stream_has_no_content() -> None:
    assert content_ratio([]) == 0.0


def test_an_all_content_stream_is_one() -> None:
    assert content_ratio(["board", "schedule"]) == 1.0


def test_an_all_stopword_stream_is_zero() -> None:
    assert content_ratio(["the", "of", "and"]) == 0.0


def test_shingling_still_keeps_the_function_words() -> None:
    tokens = list(words_only(tokenize(CLAUSE)))
    with_stopwords = {shingle.value for shingle in word_shingles(tokens, 3)}
    trimmed = list(words_only(tokenize(" ".join(content_tokens(words(CLAUSE))))))
    without = {shingle.value for shingle in word_shingles(trimmed, 3)}
    assert not with_stopwords & without


def test_the_modal_verbs_are_weighted_down_not_removed_from_the_language() -> None:
    assert is_stopword("shall")
    assert is_stopword("may")
    assert "shall" in words("the board shall act")

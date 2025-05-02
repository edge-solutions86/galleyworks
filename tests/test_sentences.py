from __future__ import annotations

import pytest

from lexdrift.canon.sentences import split_sentences
from lexdrift.util.spans import TextSpan


def pieces(text: str, **kwargs: object) -> list[str]:
    spans = split_sentences(text, **kwargs)  # type: ignore[arg-type]
    return [span.slice_of(text) for span in spans]


def test_plain_sentences_are_split() -> None:
    text = "The board shall act. The commission may object. Fees apply."
    assert pieces(text) == [
        "The board shall act.",
        "The commission may object.",
        "Fees apply.",
    ]


def test_a_statutory_citation_is_not_a_boundary() -> None:
    text = "W.S. 41-3-101 is amended. The board shall act."
    assert pieces(text) == ["W.S. 41-3-101 is amended.", "The board shall act."]


def test_abbreviations_are_respected() -> None:
    text = "See Sec. 4 for details. No. 1188 applies. Use e.g. water rights here."
    assert pieces(text) == [
        "See Sec. 4 for details.",
        "No. 1188 applies.",
        "Use e.g. water rights here.",
    ]


def test_single_letter_initials_do_not_split() -> None:
    assert pieces("Filed by J. Smith on Tuesday.") == ["Filed by J. Smith on Tuesday."]


def test_a_lowercase_continuation_is_not_a_boundary() -> None:
    assert pieces("published no. 4 and the rest.") == ["published no. 4 and the rest."]


def test_question_and_exclamation_marks_split() -> None:
    assert pieces("Is it due? It is due! Pay now.") == ["Is it due?", "It is due!", "Pay now."]


def test_a_closing_quote_stays_with_its_sentence() -> None:
    text = 'The term means "water right." The board shall publish.'
    assert pieces(text) == ['The term means "water right."', "The board shall publish."]


def test_a_closing_parenthesis_stays_with_its_sentence() -> None:
    text = "The fee applies (see subsection (b).) The board shall act."
    assert pieces(text)[1] == "The board shall act."


def test_the_last_sentence_needs_no_terminator() -> None:
    assert pieces("First one. Second one without a stop") == [
        "First one.",
        "Second one without a stop",
    ]


def test_whitespace_only_input_yields_nothing() -> None:
    assert pieces("   \n\t ") == []
    assert pieces("") == []


def test_spans_are_trimmed_of_surrounding_whitespace() -> None:
    text = "  First.   Second.  "
    spans = split_sentences(text)
    assert spans == [TextSpan(2, 8), TextSpan(11, 18)]


def test_offset_shifts_every_span() -> None:
    spans = split_sentences("A. B.", offset=100)
    assert spans[0].start == 100


def test_clause_breaking_is_off_by_default() -> None:
    text = "the priority date; and the acreage irrigated."
    assert len(pieces(text)) == 1


def test_clause_breaking_splits_on_semicolon_and() -> None:
    text = "the priority date; and the acreage irrigated."
    assert pieces(text, break_on_clause=True) == [
        "the priority date;",
        "and the acreage irrigated.",
    ]


def test_clause_breaking_splits_on_semicolon_or() -> None:
    text = "by mail; or by publication."
    assert pieces(text, break_on_clause=True) == ["by mail;", "or by publication."]


def test_a_bare_semicolon_is_not_a_clause_break() -> None:
    text = "by mail; by publication."
    assert pieces(text, break_on_clause=True) == [text]


@pytest.mark.parametrize("terminator", [".", "?", "!"])
def test_a_number_can_open_a_sentence(terminator: str) -> None:
    text = f"The rate applies{terminator} 2025 is the first year."
    assert len(pieces(text)) == 2

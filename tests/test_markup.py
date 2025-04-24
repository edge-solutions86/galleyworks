from __future__ import annotations

import pytest

from lexdrift.errors import ParseError
from lexdrift.ingest.markup import ChangeKind, has_markup, parse_markup
from lexdrift.util.spans import TextSpan

SIMPLE = "not later than {-sixty (60)-}{+ninety (90)+} days after the close."


def test_plain_text_passes_through_unchanged() -> None:
    parsed = parse_markup("The board shall publish.")
    assert parsed.current == parsed.proposed == "The board shall publish."
    assert parsed.changes == ()
    assert not parsed.is_amendment


def test_both_readings_are_produced() -> None:
    parsed = parse_markup(SIMPLE)
    assert parsed.current == "not later than sixty (60) days after the close."
    assert parsed.proposed == "not later than ninety (90) days after the close."
    assert parsed.is_amendment


def test_changes_are_classified_and_located() -> None:
    parsed = parse_markup(SIMPLE)
    assert [change.kind for change in parsed.changes] == [ChangeKind.STRIKE, ChangeKind.INSERT]
    assert [change.text for change in parsed.changes] == ["sixty (60)", "ninety (90)"]
    strike = parsed.changes[0]
    assert strike.span.slice_of(SIMPLE) == "{-sixty (60)-}"
    assert strike.is_strike


def test_struck_and_inserted_views() -> None:
    parsed = parse_markup(SIMPLE)
    assert [c.text for c in parsed.struck()] == ["sixty (60)"]
    assert [c.text for c in parsed.inserted()] == ["ninety (90)"]


def test_current_offsets_point_back_at_the_markup() -> None:
    parsed = parse_markup(SIMPLE)
    start = parsed.current.index("sixty")
    assert SIMPLE[parsed.current_map.to_source(start) :].startswith("sixty")


def test_proposed_offsets_point_back_at_the_markup() -> None:
    parsed = parse_markup(SIMPLE)
    start = parsed.proposed.index("ninety")
    assert SIMPLE[parsed.proposed_map.to_source(start) :].startswith("ninety")


def test_offsets_of_the_shared_tail_agree() -> None:
    parsed = parse_markup(SIMPLE)
    current_tail = parsed.current.index("days")
    proposed_tail = parsed.proposed.index("days")
    assert parsed.current_map.to_source(current_tail) == SIMPLE.index("days")
    assert parsed.proposed_map.to_source(proposed_tail) == SIMPLE.index("days")


def test_maps_declare_the_markup_length() -> None:
    parsed = parse_markup(SIMPLE)
    assert parsed.current_map.source_length == len(SIMPLE)
    assert parsed.proposed_map.source_length == len(SIMPLE)
    assert parsed.current_map.target_length == len(parsed.current)


def test_an_insert_only_amendment() -> None:
    parsed = parse_markup("the board{+ and the commission+} shall act")
    assert parsed.current == "the board shall act"
    assert parsed.proposed == "the board and the commission shall act"


def test_a_strike_only_amendment() -> None:
    parsed = parse_markup("the board{- and the commission-} shall act")
    assert parsed.current == "the board and the commission shall act"
    assert parsed.proposed == "the board shall act"


def test_an_empty_insert_is_allowed() -> None:
    parsed = parse_markup("a{++}b")
    assert parsed.current == "ab"
    assert parsed.proposed == "ab"
    assert parsed.changes[0].text == ""


def test_markup_spanning_lines() -> None:
    parsed = parse_markup("keep {-drop\nthese-} keep")
    assert parsed.proposed == "keep  keep"
    assert parsed.current == "keep drop\nthese keep"


def test_has_markup_detects_well_formed_tags() -> None:
    assert has_markup(SIMPLE)
    assert not has_markup("no tags at all")
    assert not has_markup("a stray {- opener")


@pytest.mark.parametrize("text", ["a stray {- opener", "a stray {+ opener", "{-closed-} then {+"])
def test_unclosed_tags_are_rejected(text: str) -> None:
    with pytest.raises(ParseError, match="unclosed tag"):
        parse_markup(text)


def test_an_unclosed_tag_reports_its_line_and_column() -> None:
    with pytest.raises(ParseError) as caught:
        parse_markup("first line\nsecond {- line")
    assert caught.value.line == 2
    assert caught.value.column == 7


def test_span_of_a_change_is_the_whole_tag() -> None:
    parsed = parse_markup("x{+abc+}y")
    assert parsed.changes[0].span == TextSpan(1, 8)

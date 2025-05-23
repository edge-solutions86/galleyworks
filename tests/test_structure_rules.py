from __future__ import annotations

from lexdrift.canon.pipeline import apply_edits
from lexdrift.canon.structure_rules import (
    EnactingClauseRule,
    EnumerationRule,
    find_enumeration_labels,
)
from lexdrift.config import CanonConfig


def drop_labels(text: str, config: CanonConfig | None = None) -> str:
    edits = list(EnumerationRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


def drop_enacting(text: str, config: CanonConfig | None = None) -> str:
    edits = list(EnactingClauseRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


def test_a_leading_label_is_dropped() -> None:
    assert drop_labels("(a) the board shall act") == "the board shall act"


def test_a_label_after_a_full_stop_is_dropped() -> None:
    text = "the board shall act. (b) the commission may object"
    assert drop_labels(text) == "the board shall act. the commission may object"


def test_a_label_after_a_semicolon_is_dropped() -> None:
    text = "by mail; (ii) by publication"
    assert drop_labels(text) == "by mail; by publication"


def test_nested_labels_are_both_dropped() -> None:
    assert drop_labels("(a) (i) by mail") == "by mail"


def test_a_cited_subsection_is_kept() -> None:
    text = "as provided in subsection (b) of this section"
    assert drop_labels(text) == text


def test_a_doubled_numeral_is_not_a_label() -> None:
    text = "not later than sixty (60) days"
    assert drop_labels(text) == text


def test_labels_report_their_spans() -> None:
    text = "(a) the board"
    spans = list(find_enumeration_labels(text))
    assert [span.slice_of(text) for span in spans] == ["(a) "]


def test_a_label_at_a_line_start_is_dropped() -> None:
    assert drop_labels("lead in.\n(b) next part") == "lead in.\nnext part"


def test_the_enumeration_rule_can_be_switched_off() -> None:
    text = "(a) the board shall act"
    assert drop_labels(text, CanonConfig(drop_enumeration_labels=False)) == text


def test_the_enacting_clause_is_dropped() -> None:
    text = "Be It Enacted by the Legislature of the State of Wyoming: Section 1 applies."
    assert drop_enacting(text) == "Section 1 applies."


def test_an_enacting_clause_ending_in_a_full_stop() -> None:
    text = "Be it enacted by the people of the State of Colorado. The board shall act."
    assert drop_enacting(text) == "The board shall act."


def test_text_without_an_enacting_clause_is_untouched() -> None:
    text = "The board shall act."
    assert drop_enacting(text) == text


def test_the_enacting_rule_can_be_switched_off() -> None:
    text = "Be It Enacted by the Legislature: rest."
    assert drop_enacting(text, CanonConfig(drop_enacting_clause=False)) == text


def test_an_enacting_clause_is_not_matched_across_a_whole_document() -> None:
    text = "Be it enacted " + "x" * 400 + ". tail."
    assert drop_enacting(text) == text

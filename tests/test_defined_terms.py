from __future__ import annotations

from lexdrift.canon.defined_terms import DefinedTermRule, find_definitions
from lexdrift.canon.pipeline import apply_edits
from lexdrift.config import CanonConfig

DEFINITIONS = (
    'As used in this act, "board" means the state board of control and '
    '"appropriation" means a right to divert water.'
)


def rewrite(text: str) -> str:
    edits = list(DefinedTermRule().edits(text, CanonConfig()))
    return apply_edits(text, edits)[0]


def test_definitions_are_found() -> None:
    found = find_definitions(DEFINITIONS)
    assert [definition.term for definition in found] == ["board", "appropriation"]


def test_definition_spans_cover_the_term_only() -> None:
    definition = find_definitions(DEFINITIONS)[0]
    assert definition.span.slice_of(DEFINITIONS) == "board"


def test_definition_keys_are_folded() -> None:
    found = find_definitions('"State  Board" means the agency.')
    assert found[0].key == "state board"


def test_curly_quotes_are_recognised() -> None:
    found = find_definitions("“board” means the agency.")
    assert [definition.term for definition in found] == ["board"]


def test_single_quotes_are_recognised() -> None:
    assert [d.term for d in find_definitions("'board' means the agency.")] == ["board"]


def test_a_long_phrase_is_not_a_defined_term() -> None:
    text = '"a very long quoted phrase that runs on and on here" means something.'
    assert find_definitions(text) == []


def test_text_with_no_definitions() -> None:
    assert find_definitions("The board shall act.") == []


def test_quotes_are_stripped_from_the_definition_itself() -> None:
    assert rewrite('"board" means the agency.') == "board means the agency."


def test_quotes_are_stripped_from_later_uses() -> None:
    text = '"board" means the agency. The "board" shall publish.'
    assert rewrite(text) == "board means the agency. The board shall publish."


def test_an_undefined_quotation_keeps_its_quotes() -> None:
    text = '"board" means the agency. The report said "the schedule was late".'
    assert '"the schedule was late"' in rewrite(text)


def test_case_does_not_matter_for_later_uses() -> None:
    text = '"board" means the agency. The "Board" shall publish.'
    assert rewrite(text) == "board means the agency. The Board shall publish."


def test_multi_word_terms_are_handled() -> None:
    text = '"water right" means a right. A "water right" is transferable.'
    assert rewrite(text) == "water right means a right. A water right is transferable."


def test_two_definitions_in_one_sentence() -> None:
    rewritten = rewrite(DEFINITIONS)
    assert '"' not in rewritten
    assert "board means the state board of control" in rewritten


def test_a_document_with_no_definitions_is_untouched() -> None:
    text = 'The report said "late".'
    assert rewrite(text) == text

from __future__ import annotations

import pytest

from lexdrift.canon.citations import CitationRule, find_citations
from lexdrift.canon.pipeline import apply_edits
from lexdrift.config import CanonConfig


def rewrite(text: str, config: CanonConfig | None = None) -> str:
    edits = list(CitationRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


@pytest.mark.parametrize(
    "text",
    [
        "W.S. 41-3-101",
        "W.S. 41-3-101",
        "Wyo. Stat. Ann. § 41-3-101",
        "WS 41-3-101",
    ],
)
def test_state_reporters_all_collapse_to_one_form(text: str) -> None:
    assert rewrite(text) == "cite wy 41-3-101"


def test_a_bare_section_word_is_local() -> None:
    assert rewrite("section 41-3-101") == "cite local 41-3-101"
    assert rewrite("§ 41-3-101") == "cite local 41-3-101"
    assert rewrite("§§ 41-3-101") == "cite local 41-3-101"


def test_subsections_are_preserved() -> None:
    assert rewrite("W.S. 41-3-101(a)(ii)") == "cite wy 41-3-101(a)(ii)"


def test_subsection_case_is_folded() -> None:
    assert rewrite("W.S. 41-3-101(A)") == "cite wy 41-3-101(a)"


def test_a_federal_title_is_kept() -> None:
    assert rewrite("42 U.S.C. § 1983") == "cite usc42 1983"


def test_federal_regulations_are_recognised() -> None:
    assert rewrite("40 C.F.R. 122.26") == "cite cfr40 122.26"


def test_two_reporters_are_not_confused() -> None:
    text = "under W.S. 41-3-101 and 42 U.S.C. 1983"
    assert rewrite(text) == "under cite wy 41-3-101 and cite usc42 1983"


def test_a_leading_count_before_section_is_not_a_title() -> None:
    assert rewrite("amends 3 sections 41-3-101") == "amends 3 sections 41-3-101"


def test_plain_prose_is_untouched() -> None:
    text = "The board shall publish a schedule."
    assert rewrite(text) == text


def test_a_lone_number_is_not_a_citation() -> None:
    assert rewrite("not later than 41-3-101 days") == "not later than 41-3-101 days"


def test_citations_report_their_spans() -> None:
    text = "see W.S. 41-3-101 today"
    citation = next(find_citations(text))
    assert citation.span.slice_of(text) == "W.S. 41-3-101"
    assert citation.reporter == "wy"
    assert citation.number == "41-3-101"


def test_the_rule_can_be_switched_off() -> None:
    text = "W.S. 41-3-101"
    assert rewrite(text, CanonConfig(normalize_citations=False)) == text


def test_two_stylings_of_one_provision_become_identical() -> None:
    first = rewrite("as provided in W.S. 41-3-101(a), the board shall act")
    second = rewrite("as provided in section 41-3-101(a), the board shall act")
    assert first.replace("cite wy", "cite X") == second.replace("cite local", "cite X")


def test_a_lettered_statute_number_is_recognised() -> None:
    assert rewrite("W.S. 41-3-101a") == "cite wy 41-3-101a"


def test_dotted_numbering_is_recognised() -> None:
    assert rewrite("section 15.04.020") == "cite local 15.04.020"

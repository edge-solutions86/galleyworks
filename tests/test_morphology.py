from __future__ import annotations

import pytest

from lexdrift.canon.morphology import JOINED_PREFIXES, MorphologyRule, fold_possessive
from lexdrift.canon.pipeline import apply_edits
from lexdrift.canon.profiles import canonicalize, default_rules
from lexdrift.config import CanonConfig


def rewrite(text: str, config: CanonConfig | None = None) -> str:
    edits = list(MorphologyRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


def test_a_possessive_is_folded() -> None:
    assert rewrite("the board's schedule") == "the board schedule"


def test_a_plural_possessive_is_folded() -> None:
    assert rewrite("the boards' schedule") == "the boards schedule"


def test_a_curly_apostrophe_is_folded() -> None:
    assert rewrite("the board’s schedule") == "the board schedule"


def test_a_contraction_of_two_letters_is_left_alone() -> None:
    text = "a b's c"
    assert rewrite(text) == text


def test_fold_possessive_works_on_a_bare_word() -> None:
    assert fold_possessive("governor's") == "governor"
    assert fold_possessive("appropriation") == "appropriation"


@pytest.mark.parametrize("prefix", ["non", "pre", "sub", "inter", "re"])
def test_hyphenated_prefixes_close_up(prefix: str) -> None:
    assert rewrite(f"a {prefix}-resident owner") == f"a {prefix}resident owner"


def test_the_prefix_list_is_lowercase_and_unique() -> None:
    assert all(prefix == prefix.lower() for prefix in JOINED_PREFIXES)
    assert len(JOINED_PREFIXES) == len(set(JOINED_PREFIXES))


def test_a_compound_that_is_not_a_prefix_is_left_alone() -> None:
    text = "a water-right holder"
    assert rewrite(text) == text


def test_a_short_tail_is_left_alone() -> None:
    text = "the re-do order"
    assert rewrite(text) == text


def test_case_is_recognised() -> None:
    assert rewrite("Non-Resident") == "NonResident"


def test_the_rule_can_be_switched_off() -> None:
    text = "the board's non-resident owner"
    assert rewrite(text, CanonConfig(fold_variants=False)) == text


def test_the_rule_runs_in_the_default_pipeline() -> None:
    assert "morphology" in [rule.name for rule in default_rules()]


def test_two_typesettings_canonicalize_alike() -> None:
    left = canonicalize("(a)  The board's non-resident applicant shall pay.").text
    right = canonicalize("(a)  The board nonresident applicant shall pay.").text
    assert left == right


def test_several_forms_in_one_sentence() -> None:
    text = "the board's pre-approval of a non-resident co-owner"
    assert rewrite(text) == "the board preapproval of a nonresident coowner"

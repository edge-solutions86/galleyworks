from __future__ import annotations

import pytest

from lexdrift.canon.pipeline import apply_edits
from lexdrift.canon.profiles import canonicalize, default_rules
from lexdrift.canon.variants import VARIANTS, VariantSpellingRule, canonical_spelling
from lexdrift.config import CanonConfig


def rewrite(text: str, config: CanonConfig | None = None) -> str:
    edits = list(VariantSpellingRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


@pytest.mark.parametrize(
    ("written", "preferred"),
    [
        ("judgement", "judgment"),
        ("licence", "license"),
        ("offence", "offense"),
        ("authorised", "authorized"),
        ("whilst", "while"),
    ],
)
def test_variants_are_folded(written: str, preferred: str) -> None:
    assert rewrite(f"the {written} of the board") == f"the {preferred} of the board"


def test_the_preferred_form_is_left_alone() -> None:
    text = "the judgment of the board"
    assert rewrite(text) == text


def test_case_is_recognised_but_the_preferred_form_wins() -> None:
    assert rewrite("Judgement") == "judgment"


def test_a_word_that_is_not_a_variant_is_untouched() -> None:
    text = "the board shall insure the property and ensure compliance"
    assert rewrite(text) == text


def test_the_table_maps_to_real_words() -> None:
    assert all(value and value.isalpha() for value in VARIANTS.values())
    assert all(key == key.lower() for key in VARIANTS)


def test_the_table_is_idempotent() -> None:
    for preferred in set(VARIANTS.values()):
        assert canonical_spelling(preferred) == preferred


def test_canonical_spelling_passes_unknown_words_through() -> None:
    assert canonical_spelling("appropriation") == "appropriation"


def test_the_rule_can_be_switched_off() -> None:
    text = "the judgement of the board"
    assert rewrite(text, CanonConfig(fold_variants=False)) == text


def test_the_rule_runs_in_the_default_pipeline() -> None:
    assert "variant-spelling" in [rule.name for rule in default_rules()]


def test_two_spellings_canonicalize_alike() -> None:
    left = canonicalize("(a)  The licence is subject to the judgement of the board.").text
    right = canonicalize("(a)  The license is subject to the judgment of the board.").text
    assert left == right


def test_several_variants_in_one_sentence() -> None:
    text = "the authorised organisation shall practise the programme"
    assert rewrite(text) == "the authorized organization shall practice the program"


def test_a_variant_inside_a_longer_word_is_not_folded() -> None:
    text = "the licences and licencing rules"
    assert rewrite(text) == text

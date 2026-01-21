from __future__ import annotations

import pytest

from lexdrift.canon.crossrefs import SELF_REFERENCE, CrossReferenceRule
from lexdrift.canon.pipeline import apply_edits
from lexdrift.canon.profiles import canonicalize, default_rules
from lexdrift.config import CanonConfig


def rewrite(text: str, config: CanonConfig | None = None) -> str:
    edits = list(CrossReferenceRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


@pytest.mark.parametrize(
    "phrase",
    [
        "this act",
        "this section",
        "this subsection",
        "this chapter",
        "that article",
        "the preceding section",
        "the following subsection",
        "the same paragraph",
    ],
)
def test_self_references_are_folded(phrase: str) -> None:
    assert rewrite(f"under {phrase}, the board shall act") == (
        f"under {SELF_REFERENCE}, the board shall act"
    )


def test_the_plural_is_folded() -> None:
    # "the preceding sections" is one reference, article included.
    assert rewrite("these are the preceding sections") == f"these are {SELF_REFERENCE}"
    assert rewrite("under this subsections") == f"under {SELF_REFERENCE}"


def test_case_does_not_matter() -> None:
    assert rewrite("This Act applies") == f"{SELF_REFERENCE} applies"


def test_a_numbered_section_is_left_alone() -> None:
    text = "as provided in section 4 of the act"
    assert rewrite(text) == text


def test_a_statutory_citation_is_left_alone() -> None:
    text = "as provided in W.S. 41-3-101"
    assert rewrite(text) == text


def test_ordinary_prose_is_untouched() -> None:
    text = "the board shall publish a schedule"
    assert rewrite(text) == text


def test_the_words_on_either_side_are_kept_apart() -> None:
    rewritten = rewrite("nothing in this act limits the board")
    assert rewritten == f"nothing in {SELF_REFERENCE} limits the board"
    assert "in limits" not in rewritten


def test_several_references_in_one_sentence() -> None:
    text = "this section and the preceding section apply under this act"
    assert rewrite(text) == (f"{SELF_REFERENCE} and {SELF_REFERENCE} apply under {SELF_REFERENCE}")


def test_the_rule_can_be_switched_off() -> None:
    text = "under this act"
    assert rewrite(text, CanonConfig(normalize_citations=False)) == text


def test_the_rule_runs_in_the_default_pipeline() -> None:
    assert "cross-references" in [rule.name for rule in default_rules()]


def test_the_pipeline_folds_a_self_reference() -> None:
    result = canonicalize("(a)  Nothing in this act limits the board.")
    assert SELF_REFERENCE in result.text
    assert "this act" not in result.text


def test_two_acts_no_longer_share_the_boilerplate_reference() -> None:
    left = canonicalize("(a)  The provisions of this act apply to every district.").text
    right = canonicalize("(a)  The provisions of this chapter apply to every district.").text
    assert left == right

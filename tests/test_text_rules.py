from __future__ import annotations

from lexdrift.canon.pipeline import CanonPipeline, Edit, apply_edits
from lexdrift.canon.text_rules import (
    CaseFoldRule,
    UnicodeFoldRule,
    WhitespaceRule,
    runs_where_changed,
)
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan


def run(rule: object, text: str, config: CanonConfig | None = None) -> str:
    edits = list(rule.edits(text, config or CanonConfig()))  # type: ignore[attr-defined]
    return apply_edits(text, edits)[0]


def test_runs_where_changed_groups_neighbours() -> None:
    text = "ABCdefGH"
    folded = list("abcdefgh")
    edits = list(runs_where_changed(text, folded))
    assert edits == [Edit(TextSpan(0, 3), "abc"), Edit(TextSpan(6, 8), "gh")]


def test_runs_where_changed_handles_a_full_rewrite() -> None:
    edits = list(runs_where_changed("AB", ["a", "b"]))
    assert edits == [Edit(TextSpan(0, 2), "ab")]


def test_runs_where_changed_on_an_unchanged_text() -> None:
    assert list(runs_where_changed("abc", ["a", "b", "c"])) == []


def test_curly_quotes_and_dashes_become_ascii() -> None:
    text = "the “board” shall act — promptly – always"
    assert run(UnicodeFoldRule(), text) == 'the "board" shall act - promptly - always'


def test_non_breaking_space_becomes_a_space() -> None:
    assert run(UnicodeFoldRule(), "sixty days") == "sixty days"


def test_a_soft_hyphen_is_dropped() -> None:
    assert run(UnicodeFoldRule(), "appro­priation") == "appropriation"


def test_an_ellipsis_expands() -> None:
    assert run(UnicodeFoldRule(), "and so on…") == "and so on..."


def test_compatibility_forms_are_normalized() -> None:
    assert run(UnicodeFoldRule(), "ﬁle") == "file"


def test_unicode_folding_can_be_switched_off() -> None:
    text = "the “board”"
    assert run(UnicodeFoldRule(), text, CanonConfig(fold_unicode=False)) == text


def test_whitespace_runs_collapse() -> None:
    assert run(WhitespaceRule(), "the   board\n\nshall\tact") == "the board shall act"


def test_a_single_space_is_left_alone() -> None:
    assert list(WhitespaceRule().edits("a b c", CanonConfig())) == []


def test_a_lone_newline_becomes_a_space() -> None:
    assert run(WhitespaceRule(), "a\nb") == "a b"


def test_case_folding_lowercases() -> None:
    assert run(CaseFoldRule(), "The BOARD Shall") == "the board shall"


def test_case_folding_can_be_switched_off() -> None:
    assert run(CaseFoldRule(), "The BOARD", CanonConfig(fold_case=False)) == "The BOARD"


def test_the_three_rules_compose_and_still_quote_the_source() -> None:
    pipeline = CanonPipeline([UnicodeFoldRule(), WhitespaceRule(), CaseFoldRule()])
    source = "The “BOARD”   shall\npublish — promptly."
    result = pipeline.run(source)
    assert result.text == 'the "board" shall publish - promptly.'
    assert result.applied == ("unicode-fold", "whitespace", "case-fold")
    start = result.text.index("board")
    assert result.quote(TextSpan(start, start + 5)) == "BOARD"


def test_quoting_works_after_a_collapsed_run() -> None:
    pipeline = CanonPipeline([WhitespaceRule(), CaseFoldRule()])
    source = "the board    SHALL publish"
    result = pipeline.run(source)
    start = result.text.index("publish")
    assert result.quote(TextSpan(start, start + 7)) == "publish"


def test_disabled_rules_do_not_appear_in_the_trail() -> None:
    pipeline = CanonPipeline([UnicodeFoldRule(), CaseFoldRule()])
    config = CanonConfig(fold_case=False, fold_unicode=False)
    assert pipeline.run("The BOARD", config).applied == ()

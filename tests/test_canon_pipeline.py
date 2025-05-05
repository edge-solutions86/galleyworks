from __future__ import annotations

from collections.abc import Iterable

import pytest

from lexdrift.canon.pipeline import CanonPipeline, Edit, Rule, apply_edits
from lexdrift.config import CanonConfig
from lexdrift.errors import CanonError
from lexdrift.util.spans import TextSpan


class Replace:
    """Test rule: replace every occurrence of a needle."""

    def __init__(self, name: str, needle: str, replacement: str) -> None:
        self.name = name
        self._needle = needle
        self._replacement = replacement

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        start = text.find(self._needle)
        while start >= 0:
            yield Edit(TextSpan(start, start + len(self._needle)), self._replacement)
            start = text.find(self._needle, start + len(self._needle))


class Silent:
    name = "silent"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        return ()


def test_edit_reports_its_shape() -> None:
    edit = Edit(TextSpan(0, 5), "")
    assert edit.is_deletion
    assert edit.delta == -5
    assert not Edit(TextSpan(0, 1), "abc").is_deletion


def test_apply_edits_with_nothing_returns_the_text() -> None:
    text, mapping = apply_edits("unchanged", [])
    assert text == "unchanged"
    assert mapping.to_source(4) == 4


def test_apply_edits_replaces_and_maps() -> None:
    text, mapping = apply_edits("sixty days", [Edit(TextSpan(0, 5), "ninety")])
    assert text == "ninety days"
    assert mapping.to_source(7) == 6


def test_apply_edits_sorts_its_input() -> None:
    edits = [Edit(TextSpan(6, 10), "Z"), Edit(TextSpan(0, 5), "A")]
    text, _ = apply_edits("hello world", edits)
    assert text == "A Zd"


def test_overlapping_edits_are_rejected() -> None:
    edits = [Edit(TextSpan(0, 6), "x"), Edit(TextSpan(3, 9), "y")]
    with pytest.raises(CanonError, match="overlapping edits") as caught:
        apply_edits("abcdefghij", edits, rule="demo")
    assert caught.value.context["rule"] == "demo"


def test_an_edit_past_the_end_is_rejected() -> None:
    with pytest.raises(CanonError, match="past the end"):
        apply_edits("short", [Edit(TextSpan(0, 40), "x")])


def test_abutting_edits_are_allowed() -> None:
    edits = [Edit(TextSpan(0, 3), "A"), Edit(TextSpan(3, 6), "B")]
    text, _ = apply_edits("abcdef", edits)
    assert text == "AB"


def test_pipeline_reports_its_rules() -> None:
    pipeline = CanonPipeline([Replace("one", "a", "b"), Silent()])
    assert pipeline.rule_names == ("one", "silent")


def test_duplicate_rule_names_are_rejected() -> None:
    with pytest.raises(CanonError, match="duplicate rule names"):
        CanonPipeline([Replace("same", "a", "b"), Replace("same", "c", "d")])


def test_pipeline_runs_rules_in_order() -> None:
    pipeline = CanonPipeline([Replace("first", "cat", "dog"), Replace("second", "dog", "fox")])
    result = pipeline.run("the cat sat")
    assert result.text == "the fox sat"
    assert result.applied == ("first", "second")


def test_a_rule_that_does_nothing_is_not_recorded() -> None:
    pipeline = CanonPipeline([Silent(), Replace("real", "a", "b")])
    assert pipeline.run("a").applied == ("real",)
    assert pipeline.run("zzz").applied == ()


def test_result_quotes_the_original_words() -> None:
    pipeline = CanonPipeline([Replace("fold", "SIXTY", "sixty")])
    result = pipeline.run("not later than SIXTY days")
    start = result.text.index("sixty")
    quoted = result.quote(TextSpan(start, start + 5))
    assert quoted == "SIXTY"


def test_mapping_survives_several_rules() -> None:
    pipeline = CanonPipeline(
        [Replace("a", "  ", " "), Replace("b", "SHALL", "shall"), Replace("c", "!", "")]
    )
    source = "the  board  SHALL  act!"
    result = pipeline.run(source)
    assert result.text == "the board shall act"
    start = result.text.index("shall")
    assert result.quote(TextSpan(start, start + 5)) == "SHALL"


def test_rules_satisfy_the_protocol() -> None:
    assert isinstance(Replace("x", "a", "b"), Rule)
    assert isinstance(Silent(), Rule)


def test_pipeline_accepts_an_explicit_config() -> None:
    pipeline = CanonPipeline([Replace("x", "a", "b")])
    assert pipeline.run("aaa", CanonConfig(fold_case=False)).text == "bbb"

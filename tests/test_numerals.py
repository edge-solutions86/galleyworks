from __future__ import annotations

from lexdrift.canon.numerals import NumeralRule, normalize_numerals
from lexdrift.canon.pipeline import apply_edits
from lexdrift.config import CanonConfig


def rewrite(text: str, config: CanonConfig | None = None) -> str:
    edits = list(NumeralRule().edits(text, config or CanonConfig()))
    return apply_edits(text, edits)[0]


def test_a_doubled_numeral_keeps_only_the_digits() -> None:
    assert rewrite("not later than sixty (60) days") == "not later than 60 days"


def test_a_doubled_numeral_with_a_hyphenated_word() -> None:
    assert rewrite("within twenty-one (21) days") == "within 21 days"


def test_money_written_twice_collapses() -> None:
    assert rewrite("a fee of one hundred dollars ($100.00)") == "a fee of usd 100"


def test_bare_money_is_normalized() -> None:
    assert rewrite("a fee of $1,500.00") == "a fee of usd 1500"


def test_money_without_cents() -> None:
    assert rewrite("$250") == "usd 250"


def test_money_keeps_real_cents() -> None:
    assert rewrite("$0.75") == "usd 0.75"


def test_percent_written_twice_collapses() -> None:
    assert rewrite("a rate of five percent (5%)") == "a rate of 5 pct"


def test_per_centum_is_recognised() -> None:
    assert rewrite("sixty-five per centum (65%)") == "65 pct"


def test_a_bare_percentage_is_normalized() -> None:
    assert rewrite("increase of 7.5%") == "increase of 7.5 pct"


def test_dates_become_iso() -> None:
    assert rewrite("effective July 1, 2025") == "effective date 2025-07-01"


def test_dates_without_a_comma() -> None:
    assert rewrite("on March 15 2026") == "on date 2026-03-15"


def test_ordinals_lose_their_suffix() -> None:
    assert rewrite("the 1st day and the 22nd day") == "the 1 day and the 22 day"


def test_money_wins_over_the_doubled_numeral_pattern() -> None:
    text = "one hundred dollars ($100.00) per acre"
    assert rewrite(text) == "usd 100 per acre"


def test_percent_wins_over_the_doubled_numeral_pattern() -> None:
    assert rewrite("five percent (5%) of the total") == "5 pct of the total"


def test_edits_never_overlap() -> None:
    text = "sixty (60) days, one hundred dollars ($100.00), and five percent (5%)"
    edits = list(normalize_numerals(text))
    for earlier, later in zip(edits, edits[1:], strict=False):
        assert earlier.span.end <= later.span.start


def test_several_quantities_in_one_sentence() -> None:
    text = "pay one hundred dollars ($100.00) within thirty (30) days at five percent (5%)"
    assert rewrite(text) == "pay usd 100 within 30 days at 5 pct"


def test_plain_prose_is_untouched() -> None:
    text = "The board shall publish a schedule."
    assert rewrite(text) == text


def test_a_lone_number_is_left_alone() -> None:
    assert rewrite("section 4 applies") == "section 4 applies"


def test_the_rule_can_be_switched_off() -> None:
    text = "sixty (60) days"
    assert rewrite(text, CanonConfig(normalize_numbers=False)) == text

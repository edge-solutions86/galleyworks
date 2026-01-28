from __future__ import annotations

import pytest

from lexdrift.canon.profiles import PROFILES, build_pipeline, canonicalize, default_rules, profile
from lexdrift.config import CanonConfig
from lexdrift.errors import ConfigError
from lexdrift.util.spans import TextSpan

ACT = (
    "Be It Enacted by the Legislature of the State of Wyoming:  "
    '(a)  The “board” means the state board.  The "board" shall publish '
    "under W.S. 41-3-101(a) not later than sixty (60) days, "
    "and shall charge one hundred dollars ($100.00) effective July 1, 2025."
)


def test_rule_order_is_fixed() -> None:
    assert [rule.name for rule in default_rules()] == [
        "unicode-fold",
        "whitespace",
        "defined-terms",
        "citations",
        "cross-references",
        "numerals",
        "enumeration",
        "enacting-clause",
        "case-fold",
        "variant-spelling",
    ]


def test_the_pipeline_holds_every_rule() -> None:
    assert build_pipeline().rule_names == tuple(rule.name for rule in default_rules())


def test_the_default_profile_folds_the_whole_act() -> None:
    result = canonicalize(ACT)
    assert "be it enacted" not in result.text
    assert "(a) the" not in result.text
    assert "cite wy 41-3-101(a)" in result.text
    assert "60 days" in result.text
    assert "usd 100" in result.text
    assert "date 2025-07-01" in result.text
    assert '"' not in result.text


def test_the_result_still_quotes_the_source() -> None:
    result = canonicalize(ACT)
    start = result.text.index("shall publish")
    assert result.quote(TextSpan(start, start + 13)) == "shall publish"


def test_the_verbatim_profile_changes_only_typography() -> None:
    result = canonicalize(ACT, profile("verbatim"))
    assert "Be It Enacted" in result.text
    assert "W.S. 41-3-101(a)" in result.text
    assert "sixty (60) days" in result.text
    assert "(a)  The" not in result.text


def test_the_municipal_profile_leaves_citations_alone() -> None:
    result = canonicalize("adopted under section 15.04.020 today", profile("municipal"))
    assert "section 15.04.020" in result.text


def test_the_federal_profile_keeps_the_enacting_clause() -> None:
    result = canonicalize("Be it enacted by the Congress: the rate is 5%.", profile("federal"))
    assert "be it enacted" in result.text
    assert "5%" in result.text


def test_profiles_are_named_consistently() -> None:
    for name, config in PROFILES.items():
        expected = "default" if name == "default" else name
        assert config.profile == expected


def test_an_unknown_profile_names_the_known_ones() -> None:
    with pytest.raises(ConfigError, match="unknown canonicalization profile") as caught:
        profile("nowhere")
    assert "verbatim" in caught.value.context["known"]


def test_two_stylings_of_one_clause_canonicalize_alike() -> None:
    first = canonicalize(
        "(a)  The board shall act under W.S. 41-3-101 within sixty (60) days."
    ).text
    second = canonicalize("(c)  The BOARD shall act under section 41-3-101 within 60 days.").text
    assert first.replace("cite wy", "cite X") == second.replace("cite local", "cite X")


def test_an_empty_document_survives() -> None:
    result = canonicalize("")
    assert result.text == ""
    assert result.applied == ()


def test_a_custom_config_is_honoured() -> None:
    result = canonicalize("The BOARD shall act.", CanonConfig(fold_case=False))
    assert "BOARD" in result.text

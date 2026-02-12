"""Properties the canonicalization pipeline has to hold for any input.

The example tests pin what each rule does to text a drafter would write. These
pin the invariants the offset map depends on, which is where a rule that looks
right in isolation breaks the report that quotes it.
"""

from __future__ import annotations

from hypothesis import given, settings
from hypothesis import strategies as st

from lexdrift.canon.profiles import build_pipeline, canonicalize, profile
from lexdrift.canon.tokens import tokenize
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

_ALPHABET = st.sampled_from(
    list("abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123456789 .,;:()§-'\"\n\t “”—")
)
legal_text = st.text(alphabet=_ALPHABET, max_size=200)


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_canonicalization_never_raises(text: str) -> None:
    canonicalize(text)


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_the_mapping_covers_the_result(text: str) -> None:
    result = canonicalize(text)
    assert result.mapping.target_length == len(result.text)
    assert result.mapping.source_length == len(text)


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_every_canonical_offset_maps_into_the_source(text: str) -> None:
    result = canonicalize(text)
    for offset in range(0, len(result.text) + 1, max(1, len(result.text) // 8 or 1)):
        source = result.mapping.to_source(offset)
        assert 0 <= source <= len(text)


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_spans_never_invert(text: str) -> None:
    result = canonicalize(text)
    limit = len(result.text)
    for start in range(0, limit, max(1, limit // 6 or 1)):
        end = min(limit, start + 5)
        span = result.to_source(TextSpan(start, end))
        assert span.start <= span.end
        assert span.end <= len(text)


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_canonicalization_is_idempotent_on_its_own_output(text: str) -> None:
    once = canonicalize(text).text
    twice = canonicalize(once).text
    assert twice == once


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_the_verbatim_profile_only_touches_typography(text: str) -> None:
    result = canonicalize(text, profile("verbatim"))
    assert result.text.count("(") == text.count("(")
    assert result.text.count(")") == text.count(")")


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_the_result_is_never_longer_than_a_bounded_multiple(text: str) -> None:
    result = canonicalize(text)
    assert len(result.text) <= max(32, len(text) * 4)


@given(legal_text)
@settings(max_examples=60, deadline=None)
def test_token_spans_stay_inside_the_canonical_text(text: str) -> None:
    result = canonicalize(text)
    for token in tokenize(result.text):
        assert token.span.end <= len(result.text)
        assert token.span.slice_of(result.text) == token.text


@given(legal_text)
@settings(max_examples=40, deadline=None)
def test_disabling_every_rule_leaves_the_text_alone(text: str) -> None:
    inert = CanonConfig(
        fold_case=False,
        fold_unicode=False,
        fold_variants=False,
        normalize_numbers=False,
        normalize_citations=False,
        drop_enumeration_labels=False,
        drop_enacting_clause=False,
    )
    result = build_pipeline().run(text, inert)
    assert result.text.replace(" ", "") == text.replace(" ", "").replace("\n", "").replace("\t", "")


@given(legal_text)
@settings(max_examples=40, deadline=None)
def test_the_applied_trail_only_names_real_rules(text: str) -> None:
    known = set(build_pipeline().rule_names)
    assert set(canonicalize(text).applied) <= known

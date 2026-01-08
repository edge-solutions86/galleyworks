from __future__ import annotations

from golden_corpus import golden_instruments
from lexdrift.config import MatchConfig
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.index.base import SegmentRef
from lexdrift.match.align import Anchor, Passage
from lexdrift.match.engine import Matcher, SegmentMatch
from lexdrift.match.explain import Confidence, explain
from lexdrift.match.scoring import score_pair
from lexdrift.pipeline import Engine
from lexdrift.util.spans import TextSpan


def passage(qs: int, qe: int, os_: int, oe: int) -> Passage:
    anchor = Anchor(
        query_position=qs,
        other_position=os_,
        value=qs,
        query=TextSpan(qs, qe),
        other=TextSpan(os_, oe),
    )
    return Passage(query=TextSpan(qs, qe), other=TextSpan(os_, oe), anchors=(anchor,))


def synthetic(
    *, passages: tuple[Passage, ...], shared: int = 9, other_jurisdiction: str = "wy"
) -> SegmentMatch:
    query_key = InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311")
    other_key = InstrumentKey(other_jurisdiction, "2025", Chamber.LOWER, "HB0142")
    left = list(range(10))
    right = list(range(10 - shared, 20 - shared))
    return SegmentMatch(
        query=SegmentRef(query_key, 0),
        query_path="1(a)",
        other=SegmentRef(other_key, 0),
        other_path="1(a)",
        score=score_pair(left, right),
        passages=passages,
    )


def test_a_wholesale_copy_reads_as_strong() -> None:
    match = synthetic(
        passages=(
            passage(0, 800, 0, 800),
            passage(900, 1400, 900, 1400),
            passage(1500, 1900, 1500, 1900),
        ),
        shared=10,
    )
    result = explain(match)
    assert result.confidence is Confidence.STRONG
    assert result.is_strong
    assert "strong evidence" in result.headline


def test_a_single_short_passage_reads_as_weak() -> None:
    match = synthetic(passages=(passage(0, 95, 0, 95),), shared=4)
    assert explain(match).confidence is Confidence.WEAK


def test_the_headline_names_both_paths() -> None:
    match = synthetic(passages=(passage(0, 400, 0, 400),))
    assert "1(a) reuses wy/2025/lower/HB0142 1(a)" in explain(match).headline


def test_the_containment_is_always_stated() -> None:
    result = explain(synthetic(passages=(passage(0, 400, 0, 400),)))
    assert any("shared" in reason for reason in result.reasons)
    assert any("longest shared passage" in reason for reason in result.reasons)


def test_reordered_passages_weaken_the_case() -> None:
    ordered = synthetic(
        passages=(passage(0, 500, 0, 500), passage(600, 1100, 600, 1100)), shared=10
    )
    jumbled = synthetic(
        passages=(passage(0, 500, 600, 1100), passage(600, 1100, 0, 500)), shared=10
    )
    assert any("same order" in reason for reason in explain(ordered).reasons)
    assert any("different order" in reason for reason in explain(jumbled).reasons)


def test_a_same_jurisdiction_match_is_discounted() -> None:
    across = synthetic(passages=(passage(0, 800, 0, 800),), shared=10)
    within = synthetic(passages=(passage(0, 800, 0, 800),), shared=10, other_jurisdiction="mt")
    assert any("different jurisdictions" in reason for reason in explain(across).reasons)
    assert any("same jurisdiction" in reason for reason in explain(within).reasons)


def test_many_passages_are_mentioned() -> None:
    match = synthetic(
        passages=(
            passage(0, 200, 0, 200),
            passage(300, 500, 300, 500),
            passage(600, 800, 600, 800),
        )
    )
    assert any("3 separate places" in reason for reason in explain(match).reasons)


def test_a_stricter_config_raises_the_bar() -> None:
    match = synthetic(passages=(passage(0, 400, 0, 400),), shared=8)
    loose = explain(match, MatchConfig(min_passage_chars=50))
    strict = explain(match, MatchConfig(min_passage_chars=400))
    assert loose.confidence != strict.confidence or loose.reasons != strict.reasons


def test_a_real_match_is_explained() -> None:
    result = Engine().run_over(golden_instruments())
    match = next(match for match in result.matches if match.crosses_jurisdictions)
    explanation = explain(match)
    assert explanation.headline
    assert len(explanation.reasons) >= 3


def test_every_real_match_gets_a_confidence() -> None:
    engine = Engine()
    engine.run_over(golden_instruments())
    assert isinstance(engine.matcher(), Matcher)
    result = Engine().run_over(golden_instruments())
    assert all(explain(match).confidence in set(Confidence) for match in result.matches)

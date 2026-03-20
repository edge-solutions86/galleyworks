from __future__ import annotations

import pytest

from conftest_corpus import fingerprints, make_bill
from lexdrift.canon.tokens import tokenize, words_only
from lexdrift.config import ShingleConfig, ShingleMode
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.fingerprint.shingle import MIN_ADAPTIVE_WIDTH, effective_width, shingles_for
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher

SHORT_CLAUSE = "The board acts promptly."
CONFIG = ShingleConfig(width=5, winnow_window=4)


def word_count(text: str) -> int:
    return len(list(words_only(tokenize(text))))


def test_a_long_text_keeps_the_configured_width() -> None:
    assert effective_width(50, CONFIG) == 5


def test_a_text_exactly_the_width_keeps_it() -> None:
    assert effective_width(5, CONFIG) == 5


def test_a_short_text_narrows_the_window() -> None:
    assert effective_width(3, CONFIG) == 3
    assert effective_width(4, CONFIG) == 4


def test_the_window_never_falls_below_the_floor() -> None:
    assert effective_width(1, CONFIG) == MIN_ADAPTIVE_WIDTH
    assert effective_width(0, CONFIG) == MIN_ADAPTIVE_WIDTH


@pytest.mark.parametrize("count", [0, 1, 2, 5, 40])
def test_the_window_never_exceeds_the_configured_one(count: int) -> None:
    assert effective_width(count, CONFIG) <= CONFIG.width


def test_a_short_segment_now_produces_shingles() -> None:
    assert word_count(SHORT_CLAUSE) < CONFIG.width
    assert shingles_for(SHORT_CLAUSE, CONFIG)
    assert shingles_for(SHORT_CLAUSE, CONFIG, adaptive=False) == []


def test_a_long_segment_is_unaffected() -> None:
    text = " ".join(f"word{n}" for n in range(30))
    assert shingles_for(text, CONFIG) == shingles_for(text, CONFIG, adaptive=False)


def test_character_mode_narrows_too() -> None:
    config = ShingleConfig(mode=ShingleMode.CHARACTER, width=9)
    assert shingles_for("abc", config)
    assert shingles_for("abc", config, adaptive=False) == []


def test_a_short_subsection_is_fingerprinted() -> None:
    instrument = make_bill(jurisdiction="wy", number="HB0142", body=SHORT_CLAUSE)
    records = Fingerprinter().fingerprint(instrument)
    short = next(record for record in records if record.path == "1(a)")
    assert short.selected


def test_a_short_copied_subsection_is_matched() -> None:
    index = MemoryIndex()
    index.add_all(fingerprints(make_bill(jurisdiction="wy", number="HB0142", body=SHORT_CLAUSE)))
    probe = make_bill(jurisdiction="mt", number="HB0311", body=SHORT_CLAUSE)
    matches = Matcher(index).match_instrument(probe)
    assert matches


def test_an_empty_text_still_yields_nothing() -> None:
    assert shingles_for("", CONFIG) == []

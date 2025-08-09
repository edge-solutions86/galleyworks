from __future__ import annotations

import pytest

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.errors import ConfigError
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.fingerprint.shingle import Shingle
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.match.boilerplate import BoilerplateFilter, learn_boilerplate
from lexdrift.util.spans import TextSpan


def fake(values: list[int], index: int = 0) -> SegmentFingerprint:
    return SegmentFingerprint(
        key=InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"),
        segment_index=index,
        path=str(index),
        token_count=50,
        signature=(),
        simhash=0,
        selected=tuple(
            Shingle(value=value, position=position, span=TextSpan(position, position + 4))
            for position, value in enumerate(values)
        ),
        canonical="",
    )


def test_an_empty_corpus_suppresses_nothing() -> None:
    learned = learn_boilerplate([])
    assert len(learned) == 0
    assert learned.segments_seen == 0


def test_a_common_fingerprint_is_suppressed() -> None:
    records = [fake([1, 2, index], index) for index in range(10, 30)]
    learned = learn_boilerplate(records, min_segments=2)
    assert 1 in learned
    assert 2 in learned
    assert 15 not in learned


def test_a_rare_fingerprint_survives() -> None:
    records = [fake([1, index], index) for index in range(10, 30)]
    learned = learn_boilerplate(records, min_segments=2)
    assert learned.apply([1, 11]) == (11,)


def test_the_absolute_floor_protects_a_small_corpus() -> None:
    records = [fake([1, 2], index) for index in range(3)]
    learned = learn_boilerplate(records, max_ratio=0.2, min_segments=5)
    assert len(learned) == 0
    assert learned.floor == 5


def test_the_ratio_raises_the_floor_on_a_large_corpus() -> None:
    records = [fake([1], index) for index in range(100)]
    learned = learn_boilerplate(records, max_ratio=0.5, min_segments=2)
    assert learned.floor == 51
    assert 1 in learned


def test_repeats_inside_one_segment_count_once() -> None:
    records = [fake([7, 7, 7], 0), fake([9], 1)]
    learned = learn_boilerplate(records, min_segments=2)
    assert 7 not in learned


def test_apply_preserves_order() -> None:
    learned = BoilerplateFilter(suppressed=frozenset({2}))
    assert learned.apply([1, 2, 3, 2, 4]) == (1, 3, 4)


def test_keeps_reads_a_record() -> None:
    learned = BoilerplateFilter(suppressed=frozenset({5}))
    assert learned.keeps(fake([4, 5, 6])) == (4, 6)


def test_a_default_filter_keeps_everything() -> None:
    learned = BoilerplateFilter()
    assert learned.apply([1, 2, 3]) == (1, 2, 3)
    assert len(learned) == 0


@pytest.mark.parametrize("ratio", [0.0, -0.1, 1.5])
def test_a_bad_ratio_is_rejected(ratio: float) -> None:
    with pytest.raises(ConfigError, match=r"\(0, 1\]"):
        learn_boilerplate([], max_ratio=ratio)


def test_a_bad_floor_is_rejected() -> None:
    with pytest.raises(ConfigError, match="at least one"):
        learn_boilerplate([], min_segments=0)


def test_a_real_corpus_suppresses_the_shared_recital() -> None:
    records: list[SegmentFingerprint] = []
    for index in range(8):
        body = MODEL_CLAUSE if index % 2 else LOCAL_CLAUSE
        records.extend(
            fingerprints(make_bill(jurisdiction="wy", number=f"HB{index:04d}", body=body))
        )
    learned = learn_boilerplate(records, max_ratio=0.25, min_segments=3)
    assert learned.segments_seen == len(records)
    assert learned.floor >= 3

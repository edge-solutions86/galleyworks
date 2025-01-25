from __future__ import annotations

import pytest

from lexdrift.util.spans import TextSpan, coverage, merge_spans, subtract


def test_span_rejects_negative_start() -> None:
    with pytest.raises(ValueError, match="non-negative"):
        TextSpan(-1, 4)


def test_span_rejects_inverted_bounds() -> None:
    with pytest.raises(ValueError, match="precedes start"):
        TextSpan(9, 8)


def test_empty_span_is_allowed() -> None:
    span = TextSpan(5, 5)
    assert span.is_empty
    assert span.length == 0
    assert list(span) == []


def test_contains_offset_is_half_open() -> None:
    span = TextSpan(3, 7)
    assert span.contains_offset(3)
    assert span.contains_offset(6)
    assert not span.contains_offset(7)
    assert not span.contains_offset(2)


def test_overlaps_excludes_abutting_spans() -> None:
    left = TextSpan(0, 5)
    right = TextSpan(5, 9)
    assert not left.overlaps(right)
    assert left.touches(right)


def test_intersection_returns_none_when_disjoint() -> None:
    assert TextSpan(0, 3).intersection(TextSpan(4, 8)) is None
    assert TextSpan(0, 6).intersection(TextSpan(4, 8)) == TextSpan(4, 6)


def test_hull_spans_the_gap() -> None:
    assert TextSpan(0, 2).hull(TextSpan(10, 12)) == TextSpan(0, 12)


def test_shift_and_clamp() -> None:
    assert TextSpan(2, 5).shift(3) == TextSpan(5, 8)
    assert TextSpan(2, 40).clamp(0, 10) == TextSpan(2, 10)
    assert TextSpan(20, 40).clamp(0, 10) == TextSpan(10, 10)


def test_clamp_rejects_inverted_bounds() -> None:
    with pytest.raises(ValueError, match="bounds inverted"):
        TextSpan(0, 1).clamp(5, 2)


def test_slice_of_reads_the_text() -> None:
    text = "the department shall publish"
    assert TextSpan(4, 14).slice_of(text) == "department"


def test_spans_sort_by_start_then_end() -> None:
    spans = [TextSpan(4, 9), TextSpan(0, 12), TextSpan(4, 5)]
    assert sorted(spans) == [TextSpan(0, 12), TextSpan(4, 5), TextSpan(4, 9)]


def test_merge_spans_collapses_overlaps() -> None:
    merged = merge_spans([TextSpan(0, 5), TextSpan(3, 9), TextSpan(20, 25)])
    assert merged == [TextSpan(0, 9), TextSpan(20, 25)]


def test_merge_spans_honours_gap() -> None:
    spans = [TextSpan(0, 5), TextSpan(7, 9)]
    assert merge_spans(spans) == spans
    assert merge_spans(spans, gap=2) == [TextSpan(0, 9)]


def test_merge_spans_rejects_negative_gap() -> None:
    with pytest.raises(ValueError, match="non-negative"):
        merge_spans([], gap=-1)


def test_coverage_counts_each_character_once() -> None:
    assert coverage([TextSpan(0, 10), TextSpan(5, 15)]) == 15
    assert coverage([]) == 0


def test_subtract_punches_holes() -> None:
    pieces = subtract(TextSpan(0, 20), [TextSpan(5, 8), TextSpan(12, 14)])
    assert pieces == [TextSpan(0, 5), TextSpan(8, 12), TextSpan(14, 20)]


def test_subtract_ignores_holes_outside_the_span() -> None:
    assert subtract(TextSpan(10, 20), [TextSpan(0, 4), TextSpan(30, 40)]) == [TextSpan(10, 20)]


def test_subtract_can_empty_a_span() -> None:
    assert subtract(TextSpan(4, 9), [TextSpan(0, 100)]) == []

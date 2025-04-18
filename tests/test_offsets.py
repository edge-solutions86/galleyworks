from __future__ import annotations

import pytest

from lexdrift.util.offsets import OffsetMap, OffsetMapBuilder, Piece
from lexdrift.util.spans import TextSpan


def test_identity_maps_every_offset_to_itself() -> None:
    identity = OffsetMap.identity(12)
    assert [identity.to_source(i) for i in range(13)] == list(range(13))


def test_identity_reports_its_lengths() -> None:
    identity = OffsetMap.identity(9)
    assert identity.source_length == 9
    assert identity.target_length == 9


def test_offsets_outside_the_target_are_rejected() -> None:
    identity = OffsetMap.identity(4)
    with pytest.raises(ValueError, match="outside target text"):
        identity.to_source(5)
    with pytest.raises(ValueError, match="outside target text"):
        identity.to_source(-1)


def test_overlapping_pieces_are_rejected() -> None:
    pieces = [
        Piece(TextSpan(0, 5), TextSpan(0, 5)),
        Piece(TextSpan(5, 9), TextSpan(3, 7)),
    ]
    with pytest.raises(ValueError, match="overlap"):
        OffsetMap(pieces, source_length=9, target_length=7)


def test_builder_tracks_a_deletion() -> None:
    builder = OffsetMapBuilder()
    builder.keep(4)
    builder.drop(6)
    builder.keep(3)
    mapping = builder.build()
    assert mapping.source_length == 13
    assert mapping.target_length == 7
    assert mapping.to_source(0) == 0
    assert mapping.to_source(4) == 10
    assert mapping.to_source(7) == 13


def test_builder_tracks_an_insertion() -> None:
    builder = OffsetMapBuilder()
    builder.keep(4)
    builder.insert(5)
    builder.keep(2)
    mapping = builder.build()
    assert mapping.to_source(4) == 4
    assert mapping.to_source(6) == 4
    assert mapping.to_source(9) == 4
    assert mapping.to_source(10) == 5


def test_builder_interpolates_across_a_replacement() -> None:
    builder = OffsetMapBuilder()
    builder.keep(2)
    builder.replace(10, 5)
    builder.keep(1)
    mapping = builder.build()
    assert mapping.to_source(2) == 2
    assert mapping.to_source(4) == 6
    assert mapping.to_source(7) == 12


def test_builder_ignores_empty_moves() -> None:
    builder = OffsetMapBuilder()
    builder.keep(0)
    builder.drop(0)
    assert builder.build().pieces == ()


def test_builder_rejects_negative_runs() -> None:
    with pytest.raises(ValueError, match="cannot be negative"):
        OffsetMapBuilder().keep(-1)


def test_builder_exposes_its_cursors() -> None:
    builder = OffsetMapBuilder()
    builder.keep(3)
    builder.drop(2)
    assert builder.source_cursor == 5
    assert builder.target_cursor == 3


def test_source_length_can_be_declared_to_cover_a_trailing_tail() -> None:
    builder = OffsetMapBuilder()
    builder.keep(3)
    assert builder.build(source_length=40).source_length == 40


def test_span_translation_never_inverts() -> None:
    builder = OffsetMapBuilder()
    builder.keep(3)
    builder.insert(4)
    mapping = builder.build()
    span = mapping.to_source_span(TextSpan(3, 7))
    assert span.start <= span.end
    assert span == TextSpan(3, 3)


def test_pieces_report_alignment() -> None:
    assert Piece(TextSpan(0, 4), TextSpan(0, 4)).is_aligned
    assert not Piece(TextSpan(0, 4), TextSpan(0, 2)).is_aligned
    assert not Piece(TextSpan(0, 0), TextSpan(0, 0)).is_aligned


def test_compose_chains_two_rewrites() -> None:
    first = OffsetMapBuilder()
    first.keep(4)
    first.drop(3)
    first.keep(3)
    a_to_b = first.build()

    second = OffsetMapBuilder()
    second.keep(2)
    second.drop(2)
    second.keep(3)
    b_to_c = second.build()

    a_to_c = a_to_b.compose(b_to_c)
    assert a_to_c.source_length == 10
    assert a_to_c.target_length == 5
    assert a_to_c.to_source(0) == 0
    assert a_to_c.to_source(2) == 7
    assert a_to_c.to_source(5) == 10


def test_compose_rejects_a_length_mismatch() -> None:
    with pytest.raises(ValueError, match="cannot compose"):
        OffsetMap.identity(4).compose(OffsetMap.identity(9))


def test_an_empty_map_answers_zero() -> None:
    empty = OffsetMap([], source_length=0, target_length=0)
    assert empty.to_source(0) == 0

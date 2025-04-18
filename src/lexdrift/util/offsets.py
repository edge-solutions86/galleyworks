"""Mapping offsets between a text and a rewritten version of it.

Canonicalization and amendment markup both rewrite text, and a match found in
the rewritten form has to be quoted from the original. An :class:`OffsetMap`
records the piecewise correspondence so that translation is exact where the
texts line up and honest where they do not: an inserted region has no source, so
it maps to the seam it was inserted at.

Build one with :class:`OffsetMapBuilder`, which is driven by the same four moves
every rewriter makes: keep, drop, insert, replace.
"""

from __future__ import annotations

from bisect import bisect_right
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from lexdrift.util.spans import TextSpan

__all__ = ["OffsetMap", "OffsetMapBuilder", "Piece"]


@dataclass(frozen=True, slots=True)
class Piece:
    """One aligned run: ``target`` corresponds to ``source``."""

    source: TextSpan
    target: TextSpan

    @property
    def is_aligned(self) -> bool:
        """True when the run was copied through unchanged in length."""

        return self.source.length == self.target.length and self.source.length > 0


class OffsetMap:
    """Translates offsets in a rewritten text back to the original."""

    __slots__ = ("_pieces", "_starts", "_source_length", "_target_length")

    def __init__(self, pieces: Sequence[Piece], *, source_length: int, target_length: int) -> None:
        self._pieces = tuple(pieces)
        self._starts = tuple(piece.target.start for piece in self._pieces)
        self._source_length = source_length
        self._target_length = target_length
        for earlier, later in zip(self._pieces, self._pieces[1:], strict=False):
            if later.target.start < earlier.target.end:
                raise ValueError("offset map pieces overlap in the target text")

    @classmethod
    def identity(cls, length: int) -> OffsetMap:
        span = TextSpan(0, length)
        return cls([Piece(span, span)], source_length=length, target_length=length)

    @property
    def pieces(self) -> tuple[Piece, ...]:
        return self._pieces

    @property
    def source_length(self) -> int:
        return self._source_length

    @property
    def target_length(self) -> int:
        return self._target_length

    def to_source(self, offset: int) -> int:
        """Map a target offset to the closest meaningful source offset."""

        if offset < 0 or offset > self._target_length:
            raise ValueError(f"offset {offset} outside target text of {self._target_length}")
        if not self._pieces:
            return 0
        position = bisect_right(self._starts, offset) - 1
        if position < 0:
            return self._pieces[0].source.start
        piece = self._pieces[position]
        if offset >= piece.target.end:
            return piece.source.end
        if piece.target.length == 0:
            return piece.source.start
        if piece.is_aligned:
            return piece.source.start + (offset - piece.target.start)
        ratio = (offset - piece.target.start) / piece.target.length
        return piece.source.start + int(ratio * piece.source.length)

    def to_source_span(self, span: TextSpan) -> TextSpan:
        start = self.to_source(span.start)
        end = self.to_source(span.end)
        return TextSpan(start, max(start, end))

    def compose(self, later: OffsetMap) -> OffsetMap:
        """Combine ``self`` (a -> b) with ``later`` (b -> c) into a -> c."""

        if later.source_length != self._target_length:
            raise ValueError(
                "cannot compose offset maps: "
                f"{self._target_length} target characters against {later.source_length}"
            )
        pieces = [
            Piece(source=self.to_source_span(piece.source), target=piece.target)
            for piece in later.pieces
        ]
        return OffsetMap(
            pieces, source_length=self._source_length, target_length=later.target_length
        )


class OffsetMapBuilder:
    """Accumulates pieces while a rewriter walks the source text."""

    __slots__ = ("_pieces", "_source", "_target")

    def __init__(self) -> None:
        self._pieces: list[Piece] = []
        self._source = 0
        self._target = 0

    @property
    def source_cursor(self) -> int:
        return self._source

    @property
    def target_cursor(self) -> int:
        return self._target

    def keep(self, length: int) -> None:
        """Copy ``length`` characters through unchanged."""

        self._advance(length, length)

    def drop(self, length: int) -> None:
        """Consume ``length`` source characters that produce no output."""

        self._advance(length, 0)

    def insert(self, length: int) -> None:
        """Emit ``length`` target characters with no source behind them."""

        self._advance(0, length)

    def replace(self, source_length: int, target_length: int) -> None:
        """Consume and emit different amounts of text."""

        self._advance(source_length, target_length)

    def _advance(self, source_length: int, target_length: int) -> None:
        if source_length < 0 or target_length < 0:
            raise ValueError("offset map runs cannot be negative")
        if source_length == 0 and target_length == 0:
            return
        piece = Piece(
            source=TextSpan(self._source, self._source + source_length),
            target=TextSpan(self._target, self._target + target_length),
        )
        self._source = piece.source.end
        self._target = piece.target.end
        self._pieces.append(piece)

    def extend(self, pieces: Iterable[Piece]) -> None:
        for piece in pieces:
            self._pieces.append(piece)
            self._source = max(self._source, piece.source.end)
            self._target = max(self._target, piece.target.end)

    def build(self, *, source_length: int | None = None) -> OffsetMap:
        return OffsetMap(
            self._pieces,
            source_length=self._source if source_length is None else source_length,
            target_length=self._target,
        )

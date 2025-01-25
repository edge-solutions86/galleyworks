"""Half-open character spans and the arithmetic the rest of the engine needs.

Every offset in lexdrift is a character offset into a specific text, and a span
is always ``[start, end)``. The canonicalizer rewrites text, so spans have to be
translatable between the raw source and the canonical form; the primitives for
that live here.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass

__all__ = [
    "TextSpan",
    "coverage",
    "merge_spans",
    "subtract",
]


@dataclass(frozen=True, slots=True, order=True)
class TextSpan:
    """A half-open ``[start, end)`` range of character offsets."""

    start: int
    end: int

    def __post_init__(self) -> None:
        if self.start < 0:
            raise ValueError(f"span start must be non-negative, got {self.start}")
        if self.end < self.start:
            raise ValueError(f"span end {self.end} precedes start {self.start}")

    @property
    def length(self) -> int:
        return self.end - self.start

    @property
    def is_empty(self) -> bool:
        return self.end == self.start

    def contains_offset(self, offset: int) -> bool:
        return self.start <= offset < self.end

    def contains(self, other: TextSpan) -> bool:
        return self.start <= other.start and other.end <= self.end

    def overlaps(self, other: TextSpan) -> bool:
        return self.start < other.end and other.start < self.end

    def touches(self, other: TextSpan) -> bool:
        """True when the spans overlap or sit immediately next to each other."""

        return self.start <= other.end and other.start <= self.end

    def intersection(self, other: TextSpan) -> TextSpan | None:
        start = max(self.start, other.start)
        end = min(self.end, other.end)
        if end <= start:
            return None
        return TextSpan(start, end)

    def hull(self, other: TextSpan) -> TextSpan:
        """The smallest span covering both operands, gap included."""

        return TextSpan(min(self.start, other.start), max(self.end, other.end))

    def shift(self, delta: int) -> TextSpan:
        return TextSpan(self.start + delta, self.end + delta)

    def clamp(self, lower: int, upper: int) -> TextSpan:
        if upper < lower:
            raise ValueError(f"clamp bounds inverted: [{lower}, {upper}]")
        start = min(max(self.start, lower), upper)
        end = min(max(self.end, lower), upper)
        return TextSpan(start, end)

    def slice_of(self, text: str) -> str:
        return text[self.start : self.end]

    def __iter__(self) -> Iterator[int]:
        return iter(range(self.start, self.end))

    def __repr__(self) -> str:
        return f"TextSpan({self.start}, {self.end})"


def merge_spans(spans: Iterable[TextSpan], *, gap: int = 0) -> list[TextSpan]:
    """Collapse overlapping spans, joining neighbours separated by <= ``gap``."""

    if gap < 0:
        raise ValueError(f"gap must be non-negative, got {gap}")
    ordered = sorted(spans)
    merged: list[TextSpan] = []
    for span in ordered:
        if merged and span.start - merged[-1].end <= gap:
            last = merged.pop()
            merged.append(TextSpan(last.start, max(last.end, span.end)))
        else:
            merged.append(span)
    return merged


def coverage(spans: Iterable[TextSpan]) -> int:
    """Total number of distinct characters covered by ``spans``."""

    return sum(span.length for span in merge_spans(spans))


def subtract(span: TextSpan, holes: Iterable[TextSpan]) -> list[TextSpan]:
    """Remove ``holes`` from ``span``, returning the surviving pieces in order."""

    pieces: list[TextSpan] = []
    cursor = span.start
    for hole in merge_spans(holes):
        if hole.end <= cursor:
            continue
        if hole.start >= span.end:
            break
        if hole.start > cursor:
            pieces.append(TextSpan(cursor, min(hole.start, span.end)))
        cursor = max(cursor, hole.end)
    if cursor < span.end:
        pieces.append(TextSpan(cursor, span.end))
    return [piece for piece in pieces if not piece.is_empty]

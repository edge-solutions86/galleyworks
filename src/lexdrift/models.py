"""The document model every stage of the engine reads and writes.

An :class:`Instrument` owns its raw text. Segments never copy that text; they
carry a :class:`~lexdrift.util.spans.TextSpan` into it, so a passage found by
the matcher can always be quoted back in the exact words the legislature
printed.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, field
from datetime import date
from enum import StrEnum

from lexdrift.errors import IngestError
from lexdrift.identity import InstrumentKey
from lexdrift.util.spans import TextSpan

__all__ = [
    "Instrument",
    "Segment",
    "SegmentKind",
    "SegmentPath",
]


class SegmentKind(StrEnum):
    """The structural role of a segment inside an instrument."""

    PREAMBLE = "preamble"
    ENACTING = "enacting"
    TITLE = "title"
    ARTICLE = "article"
    SECTION = "section"
    SUBSECTION = "subsection"
    PARAGRAPH = "paragraph"
    SUBPARAGRAPH = "subparagraph"
    ITEM = "item"

    @property
    def depth(self) -> int:
        """Nesting depth used when a parser has to decide where a label belongs."""

        return _KIND_DEPTH[self]

    @property
    def is_body(self) -> bool:
        """Preamble and enacting text are structure, not enacted substance."""

        return self not in (SegmentKind.PREAMBLE, SegmentKind.ENACTING)


_KIND_DEPTH: dict[SegmentKind, int] = {
    SegmentKind.PREAMBLE: 0,
    SegmentKind.ENACTING: 0,
    SegmentKind.TITLE: 1,
    SegmentKind.ARTICLE: 2,
    SegmentKind.SECTION: 3,
    SegmentKind.SUBSECTION: 4,
    SegmentKind.PARAGRAPH: 5,
    SegmentKind.SUBPARAGRAPH: 6,
    SegmentKind.ITEM: 7,
}


@dataclass(frozen=True, slots=True, order=True)
class SegmentPath:
    """The printed label chain that locates a segment, such as ``3(a)(ii)``."""

    labels: tuple[str, ...] = ()

    def child(self, label: str) -> SegmentPath:
        cleaned = label.strip()
        if not cleaned:
            raise IngestError("segment label is empty", path=self.dotted)
        return SegmentPath((*self.labels, cleaned))

    @property
    def depth(self) -> int:
        return len(self.labels)

    @property
    def dotted(self) -> str:
        return ".".join(self.labels)

    @property
    def printed(self) -> str:
        if not self.labels:
            return ""
        head, *rest = self.labels
        return head + "".join(f"({part})" for part in rest)

    def is_ancestor_of(self, other: SegmentPath) -> bool:
        return len(self.labels) < len(other.labels) and other.labels[: len(self.labels)] == (
            self.labels
        )

    def __str__(self) -> str:
        return self.printed


@dataclass(frozen=True, slots=True)
class Segment:
    """One addressable unit of enacted text."""

    index: int
    kind: SegmentKind
    path: SegmentPath
    span: TextSpan
    heading: str = ""
    parent: int | None = None

    def __post_init__(self) -> None:
        if self.index < 0:
            raise IngestError("segment index must be non-negative", index=self.index)
        if self.parent is not None and self.parent >= self.index:
            raise IngestError(
                "segment parent must precede the child",
                index=self.index,
                parent=self.parent,
            )

    def text_of(self, instrument: Instrument) -> str:
        return self.span.slice_of(instrument.raw_text)


@dataclass(slots=True)
class Instrument:
    """A single legislative document plus its parsed structure."""

    key: InstrumentKey
    title: str
    raw_text: str
    segments: list[Segment] = field(default_factory=list)
    introduced_on: date | None = None
    enacted_on: date | None = None
    source_format: str = "unknown"

    def __post_init__(self) -> None:
        limit = len(self.raw_text)
        for segment in self.segments:
            if segment.span.end > limit:
                raise IngestError(
                    "segment span runs past the end of the document",
                    index=segment.index,
                    end=segment.span.end,
                    length=limit,
                )

    @property
    def effective_date(self) -> date | None:
        """The date the corpus should sort this instrument by."""

        return self.enacted_on or self.introduced_on

    def body_segments(self) -> Iterator[Segment]:
        for segment in self.segments:
            if segment.kind.is_body:
                yield segment

    def segment_at(self, index: int) -> Segment:
        for segment in self.segments:
            if segment.index == index:
                return segment
        raise IngestError("no such segment", index=index, key=self.key.slug)

    def children_of(self, index: int) -> list[Segment]:
        return [segment for segment in self.segments if segment.parent == index]

    def text_for(self, segments: Sequence[Segment]) -> list[str]:
        return [segment.span.slice_of(self.raw_text) for segment in segments]

"""The index contract both backends implement.

``SegmentRef`` and ``IndexStats`` used to live in the memory backend, which made
the sqlite backend import from it for types it does not otherwise need. They
belong to the contract, not to one implementation, so they sit here and both
backends import them.

:func:`open_index` is the only place a caller has to decide between the two.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol, runtime_checkable

from lexdrift.config import EngineConfig
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.identity import InstrumentKey

__all__ = ["Index", "IndexStats", "SegmentRef", "open_index"]


@dataclass(frozen=True, slots=True, order=True)
class SegmentRef:
    """Points at one indexed segment."""

    key: InstrumentKey
    segment_index: int

    @property
    def slug(self) -> str:
        return f"{self.key.slug}#{self.segment_index}"

    @classmethod
    def of(cls, record: SegmentFingerprint) -> SegmentRef:
        return cls(key=record.key, segment_index=record.segment_index)

    @classmethod
    def parse(cls, slug: str) -> SegmentRef:
        head, _, tail = slug.rpartition("#")
        return cls(key=InstrumentKey.parse(head), segment_index=int(tail))


@dataclass(frozen=True, slots=True)
class IndexStats:
    """A summary an operator can read without dumping the index."""

    instruments: int
    segments: int
    buckets: int
    postings: int
    digest: str


@runtime_checkable
class Index(Protocol):
    """What the matcher needs from an index, whichever backend provides it."""

    config: EngineConfig

    def __len__(self) -> int: ...

    def __iter__(self) -> Iterator[SegmentFingerprint]: ...

    def add(self, record: SegmentFingerprint) -> SegmentRef: ...

    def add_all(self, records: Iterable[SegmentFingerprint]) -> int: ...

    def remove_instrument(self, key: InstrumentKey) -> int: ...

    def record(self, ref: SegmentRef) -> SegmentFingerprint: ...

    def candidates(self, record: SegmentFingerprint) -> list[SegmentRef]: ...

    def postings(self, value: int) -> list[SegmentRef]: ...

    def document_frequency(self, value: int) -> int: ...

    def instruments(self) -> list[InstrumentKey]: ...

    def stats(self) -> IndexStats: ...


def open_index(path: str | Path | None = None, config: EngineConfig | None = None) -> Index:
    """Open a durable index at ``path``, or an in-memory one when it is ``None``."""

    from lexdrift.index.memory import MemoryIndex
    from lexdrift.index.store import SqliteIndex

    if path is None:
        return MemoryIndex(config)
    return SqliteIndex(path, config)

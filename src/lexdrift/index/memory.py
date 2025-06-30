"""An in-memory index.

This is the reference implementation of the index contract. The sqlite store
implements the same operations against a file, and both are exercised by the
same tests, so a divergence between them is a test failure rather than a
mystery in production.

Three structures, all of them derived from the records added:

* ``_records``  the fingerprints themselves, by reference
* ``_buckets``  band key -> references, which is candidate generation
* ``_postings`` fingerprint value -> references, which is passage alignment
"""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from lexdrift.config import EngineConfig
from lexdrift.errors import StoreError
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.identity import InstrumentKey
from lexdrift.index.lsh import band_keys

__all__ = ["IndexStats", "MemoryIndex", "SegmentRef"]


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


@dataclass(frozen=True, slots=True)
class IndexStats:
    """A summary an operator can read without dumping the index."""

    instruments: int
    segments: int
    buckets: int
    postings: int
    digest: str


class MemoryIndex:
    """A dictionary backed index over segment fingerprints."""

    def __init__(self, config: EngineConfig | None = None) -> None:
        self.config = config or EngineConfig()
        self._records: dict[SegmentRef, SegmentFingerprint] = {}
        self._buckets: defaultdict[bytes, set[SegmentRef]] = defaultdict(set)
        self._postings: defaultdict[int, set[SegmentRef]] = defaultdict(set)

    def __len__(self) -> int:
        return len(self._records)

    def __contains__(self, ref: object) -> bool:
        return ref in self._records

    def __iter__(self) -> Iterator[SegmentFingerprint]:
        return iter(self._records.values())

    @property
    def digest(self) -> str:
        return self.config.digest()

    def add(self, record: SegmentFingerprint) -> SegmentRef:
        """Index one record, replacing any earlier record for that segment."""

        if len(record.signature) != self.config.lsh.permutations:
            raise StoreError(
                "record signature width does not match the index",
                width=len(record.signature),
                expected=self.config.lsh.permutations,
            )
        ref = SegmentRef.of(record)
        if ref in self._records:
            self._detach(ref)
        self._records[ref] = record
        for key in band_keys(record.signature, self.config.lsh):
            self._buckets[key].add(ref)
        for value in record.fingerprint_values:
            self._postings[value].add(ref)
        return ref

    def add_all(self, records: Iterable[SegmentFingerprint]) -> int:
        """Index every record, returning how many were written."""

        written = 0
        for record in records:
            self.add(record)
            written += 1
        return written

    def _detach(self, ref: SegmentRef) -> None:
        record = self._records.pop(ref)
        for key in band_keys(record.signature, self.config.lsh):
            bucket = self._buckets.get(key)
            if bucket is not None:
                bucket.discard(ref)
                if not bucket:
                    del self._buckets[key]
        for value in record.fingerprint_values:
            posting = self._postings.get(value)
            if posting is not None:
                posting.discard(ref)
                if not posting:
                    del self._postings[value]

    def remove_instrument(self, key: InstrumentKey) -> int:
        """Drop every segment of one instrument, returning how many went."""

        doomed = [ref for ref in self._records if ref.key == key]
        for ref in doomed:
            self._detach(ref)
        return len(doomed)

    def record(self, ref: SegmentRef) -> SegmentFingerprint:
        try:
            return self._records[ref]
        except KeyError as exc:
            raise StoreError("no such segment in the index", ref=ref.slug) from exc

    def candidates(self, record: SegmentFingerprint) -> list[SegmentRef]:
        """References sharing at least one band with ``record``, itself excluded."""

        found: set[SegmentRef] = set()
        own = SegmentRef.of(record)
        for key in band_keys(record.signature, self.config.lsh):
            found |= self._buckets.get(key, set())
        found.discard(own)
        return sorted(found)

    def postings(self, value: int) -> list[SegmentRef]:
        """References whose winnowed fingerprints include ``value``."""

        return sorted(self._postings.get(value, set()))

    def document_frequency(self, value: int) -> int:
        return len(self._postings.get(value, ()))

    def instruments(self) -> list[InstrumentKey]:
        return sorted({ref.key for ref in self._records})

    def stats(self) -> IndexStats:
        return IndexStats(
            instruments=len(self.instruments()),
            segments=len(self._records),
            buckets=len(self._buckets),
            postings=len(self._postings),
            digest=self.digest,
        )

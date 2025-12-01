"""Turning a parsed instrument into the records the index stores.

One record per segment. Each carries the winnowed fingerprints with spans in the
**raw** text, a MinHash signature, a SimHash and the token count, which together
are everything the matcher needs and nothing it does not.

Spans are translated back through the canonicalization offset map here, once, so
no later stage has to know that canonical text ever existed.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, replace

from lexdrift.canon.cache import CachingPipeline
from lexdrift.canon.pipeline import Canonicalizer, CanonResult
from lexdrift.canon.profiles import build_pipeline
from lexdrift.canon.tokens import tokenize, words_only
from lexdrift.config import EngineConfig
from lexdrift.fingerprint.minhash import MinHashSigner
from lexdrift.fingerprint.shingle import Shingle, shingles_for
from lexdrift.fingerprint.simhash import simhash_of_tokens
from lexdrift.fingerprint.winnow import winnow
from lexdrift.identity import InstrumentKey
from lexdrift.models import Instrument, Segment

__all__ = ["Fingerprinter", "SegmentFingerprint"]


@dataclass(frozen=True, slots=True)
class SegmentFingerprint:
    """Everything the index holds about one segment."""

    key: InstrumentKey
    segment_index: int
    path: str
    token_count: int
    signature: tuple[int, ...]
    simhash: int
    selected: tuple[Shingle, ...]
    canonical: str

    @property
    def is_empty(self) -> bool:
        return self.token_count == 0

    @property
    def fingerprint_values(self) -> tuple[int, ...]:
        return tuple(shingle.value for shingle in self.selected)

    def span_of(self, value: int) -> Shingle | None:
        for shingle in self.selected:
            if shingle.value == value:
                return shingle
        return None


class Fingerprinter:
    """Builds segment fingerprints under one engine configuration."""

    def __init__(
        self, config: EngineConfig | None = None, pipeline: Canonicalizer | None = None
    ) -> None:
        self.config = config or EngineConfig()
        # A corpus re-prints the same section in every bill that amends it, so
        # the default pipeline is the cached one.
        self._pipeline = pipeline or CachingPipeline(build_pipeline())
        self._signer = MinHashSigner(self.config.lsh)

    @property
    def pipeline(self) -> Canonicalizer:
        return self._pipeline

    @property
    def digest(self) -> str:
        return self.config.digest()

    def fingerprint(self, instrument: Instrument) -> list[SegmentFingerprint]:
        """Fingerprint every body segment of ``instrument``."""

        return [
            record
            for record in self._records(instrument, list(instrument.body_segments()))
            if not record.is_empty
        ]

    def _records(
        self, instrument: Instrument, segments: Sequence[Segment]
    ) -> Iterator[SegmentFingerprint]:
        for segment in segments:
            yield self.fingerprint_segment(instrument, segment)

    def fingerprint_segment(self, instrument: Instrument, segment: Segment) -> SegmentFingerprint:
        """Fingerprint one segment of ``instrument``."""

        raw = segment.span.slice_of(instrument.raw_text)
        canon = self._pipeline.run(raw, self.config.canon)
        tokens = [token.lowered for token in words_only(tokenize(canon.text))]
        shingles = shingles_for(canon.text, self.config.shingle)
        selected = winnow(shingles, self.config.shingle.winnow_window)
        located = tuple(_to_raw(shingle, canon, segment.span.start) for shingle in selected)
        return SegmentFingerprint(
            key=instrument.key,
            segment_index=segment.index,
            path=segment.path.printed,
            token_count=len(tokens),
            signature=self._signer.sign(shingle.value for shingle in selected),
            simhash=simhash_of_tokens(tokens),
            selected=located,
            canonical=canon.text,
        )


def _to_raw(shingle: Shingle, canon: CanonResult, base: int) -> Shingle:
    """Move a shingle span from canonical coordinates into the raw document."""

    return replace(shingle, span=canon.to_source(shingle.span).shift(base))

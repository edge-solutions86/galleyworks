"""The matcher.

Candidate generation, scoring, alignment and thresholding in one pass over an
index. Each stage narrows the previous one:

1. the index proposes candidates that share an LSH band
2. boilerplate fingerprints are removed from both sides
3. the surviving sets are scored, and anything under the containment floor goes
4. what is left is aligned into passages
5. a match with no passage long enough is dropped, because a score without a
   passage is not something a person can check

The order is deliberate. Alignment is the expensive step and it runs last, on a
candidate set the earlier stages have already cut down. Boilerplate removal runs
before scoring rather than after, because a score computed over shared
boilerplate is the number that has to be thrown away, not corrected.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass

from lexdrift.config import EngineConfig
from lexdrift.fingerprint.records import Fingerprinter, SegmentFingerprint
from lexdrift.index.base import Index, SegmentRef
from lexdrift.match.align import Passage, align
from lexdrift.match.boilerplate import BoilerplateFilter, learn_boilerplate
from lexdrift.match.scoring import PairScore, score_pair
from lexdrift.models import Instrument
from lexdrift.util.logging import get_logger

__all__ = ["Matcher", "SegmentMatch"]

_log = get_logger("match.engine")


@dataclass(frozen=True, slots=True)
class SegmentMatch:
    """One scored, aligned pair of segments."""

    query: SegmentRef
    query_path: str
    other: SegmentRef
    other_path: str
    score: PairScore
    passages: tuple[Passage, ...]

    @property
    def containment(self) -> float:
        return self.score.query_containment

    @property
    def longest_passage(self) -> int:
        return max((passage.length for passage in self.passages), default=0)

    @property
    def passage_count(self) -> int:
        return len(self.passages)

    @property
    def crosses_jurisdictions(self) -> bool:
        return self.query.key.jurisdiction != self.other.key.jurisdiction


class Matcher:
    """Finds the reuse in one instrument against an index."""

    def __init__(
        self,
        index: Index,
        config: EngineConfig | None = None,
        boilerplate: BoilerplateFilter | None = None,
    ) -> None:
        self.index = index
        self.config = config or index.config
        self.boilerplate = boilerplate or BoilerplateFilter()
        self._fingerprinter = Fingerprinter(self.config)

    @classmethod
    def over(cls, index: Index, config: EngineConfig | None = None, **kwargs: float) -> Matcher:
        """Build a matcher whose boilerplate filter is learned from the index."""

        learned = learn_boilerplate(
            iter(index),
            max_ratio=float(kwargs.get("max_ratio", 0.2)),
            min_segments=int(kwargs.get("min_segments", 5)),
        )
        _log.debug("boilerplate learned", extra={"suppressed": len(learned)})
        return cls(index, config, learned)

    def match_record(self, record: SegmentFingerprint) -> list[SegmentMatch]:
        """Match one fingerprinted segment against the index."""

        settings = self.config.match
        if record.content_ratio < settings.min_content_ratio:
            return []
        values = self.boilerplate.keeps(record)
        if not values:
            return []
        matches: list[SegmentMatch] = []
        for ref in self.index.candidates(record)[: settings.max_candidates]:
            match = self._compare(record, values, ref)
            if match is not None:
                matches.append(match)
        return self._ordered(matches)

    def _compare(
        self, record: SegmentFingerprint, values: tuple[int, ...], ref: SegmentRef
    ) -> SegmentMatch | None:
        settings = self.config.match
        other = self.index.record(ref)
        if other.content_ratio < settings.min_content_ratio:
            return None
        other_values = self.boilerplate.keeps(other)
        if not other_values:
            return None
        score = score_pair(values, other_values)
        if score.best_containment < settings.min_containment:
            return None
        passages = tuple(
            passage
            for passage in align(record, other, max_gap=settings.max_anchor_gap)
            if passage.length >= settings.min_passage_chars
        )
        if not passages:
            return None
        return SegmentMatch(
            query=SegmentRef.of(record),
            query_path=record.path,
            other=ref,
            other_path=other.path,
            score=score,
            passages=passages,
        )

    def match_instrument(self, instrument: Instrument) -> list[SegmentMatch]:
        """Match every body segment of ``instrument`` against the index."""

        found = self.match_records(self._fingerprinter.fingerprint(instrument))
        _log.debug(
            "instrument matched",
            extra={"key": instrument.key.slug, "matches": len(found)},
        )
        return found

    def match_records(self, records: Iterable[SegmentFingerprint]) -> list[SegmentMatch]:
        found: list[SegmentMatch] = []
        for record in records:
            found.extend(self.match_record(record))
        return self._ordered(found)

    def _ordered(self, matches: list[SegmentMatch]) -> list[SegmentMatch]:
        if self.config.match.order == "length":
            return sorted(matches, key=_by_length)
        return sorted(matches, key=_by_score)


def _by_length(match: SegmentMatch) -> tuple[int, str]:
    return (-match.longest_passage, match.other.slug)


def _by_score(match: SegmentMatch) -> tuple[float, str]:
    return (-match.score.best_containment, match.other.slug)

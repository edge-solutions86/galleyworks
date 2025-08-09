"""Suppressing the phrases every act repeats.

"notwithstanding any other provision of law", "is hereby amended to read as
follows", severability and effective-date clauses: a corpus of statutes is full
of runs that appear in hundreds of segments and mean nothing about copying.
They dominate the candidate lists and they drown the findings that matter.

A fingerprint seen in more than a set fraction of segments is treated as
boilerplate and removed from both sides of every comparison. The floor on
absolute count matters as much as the ratio: in a corpus of four documents,
"appears in more than twenty percent of segments" is one segment.
"""

from __future__ import annotations

from collections import Counter
from collections.abc import Collection, Iterable
from dataclasses import dataclass, field

from lexdrift.errors import ConfigError
from lexdrift.fingerprint.records import SegmentFingerprint

__all__ = ["BoilerplateFilter", "learn_boilerplate"]


@dataclass(frozen=True, slots=True)
class BoilerplateFilter:
    """The fingerprints a corpus has taught us to ignore."""

    suppressed: frozenset[int] = field(default_factory=frozenset)
    segments_seen: int = 0
    ratio: float = 0.0
    floor: int = 0

    def __len__(self) -> int:
        return len(self.suppressed)

    def __contains__(self, value: object) -> bool:
        return value in self.suppressed

    def apply(self, values: Collection[int]) -> tuple[int, ...]:
        """Drop the suppressed fingerprints from ``values``."""

        return tuple(value for value in values if value not in self.suppressed)

    def keeps(self, record: SegmentFingerprint) -> tuple[int, ...]:
        return self.apply(record.fingerprint_values)


def learn_boilerplate(
    records: Iterable[SegmentFingerprint],
    *,
    max_ratio: float = 0.2,
    min_segments: int = 5,
) -> BoilerplateFilter:
    """Learn which fingerprints are too common in this corpus to be evidence."""

    if not 0.0 < max_ratio <= 1.0:
        raise ConfigError("max_ratio must lie in (0, 1]", max_ratio=max_ratio)
    if min_segments < 1:
        raise ConfigError("min_segments must be at least one", min_segments=min_segments)
    counts: Counter[int] = Counter()
    seen = 0
    for record in records:
        seen += 1
        counts.update(set(record.fingerprint_values))
    if seen == 0:
        return BoilerplateFilter(segments_seen=0, ratio=max_ratio, floor=min_segments)
    floor = max(min_segments, int(seen * max_ratio) + 1)
    suppressed = frozenset(value for value, count in counts.items() if count >= floor)
    return BoilerplateFilter(
        suppressed=suppressed,
        segments_seen=seen,
        ratio=max_ratio,
        floor=floor,
    )

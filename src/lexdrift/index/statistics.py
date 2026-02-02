"""Describing what is in an index.

Two questions an operator asks before trusting a result. Is the corpus what I
think it is - which jurisdictions, how many instruments, how much text. And is
the index healthy for querying - how selective are the buckets, and how much of
the fingerprint space is boilerplate.

The second one is the useful one and it is easy to get wrong. A bucket holding
half the corpus is not a bug in the data; it is a signal that the banding is too
loose for this corpus and every query is paying for it.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field

from lexdrift.index.base import Index

__all__ = ["CorpusStats", "describe"]


@dataclass(frozen=True, slots=True)
class CorpusStats:
    """A measured description of an index."""

    instruments: int
    segments: int
    jurisdictions: dict[str, int] = field(default_factory=dict)
    sessions: dict[str, int] = field(default_factory=dict)
    total_tokens: int = 0
    total_fingerprints: int = 0
    distinct_fingerprints: int = 0
    widest_posting: int = 0
    digest: str = ""

    @property
    def mean_tokens(self) -> float:
        return 0.0 if self.segments == 0 else self.total_tokens / self.segments

    @property
    def mean_fingerprints(self) -> float:
        return 0.0 if self.segments == 0 else self.total_fingerprints / self.segments

    @property
    def selectivity(self) -> float:
        """Distinct fingerprints over total: one means nothing is repeated."""

        if self.total_fingerprints == 0:
            return 0.0
        return self.distinct_fingerprints / self.total_fingerprints

    @property
    def widest_posting_share(self) -> float:
        return 0.0 if self.segments == 0 else self.widest_posting / self.segments

    def lines(self) -> list[str]:
        """A short human readable summary."""

        jurisdictions = ", ".join(
            f"{name} {count}" for name, count in sorted(self.jurisdictions.items())
        )
        return [
            f"{self.instruments} instruments, {self.segments} segments",
            f"jurisdictions: {jurisdictions or 'none'}",
            f"sessions: {', '.join(sorted(self.sessions)) or 'none'}",
            f"mean {self.mean_tokens:.1f} tokens and "
            f"{self.mean_fingerprints:.1f} fingerprints per segment",
            f"{self.distinct_fingerprints} distinct of {self.total_fingerprints} fingerprints "
            f"(selectivity {self.selectivity:.2f})",
            f"widest posting covers {self.widest_posting} segments "
            f"({self.widest_posting_share:.1%} of the corpus)",
            f"engine digest {self.digest}",
        ]


def describe(index: Index) -> CorpusStats:
    """Measure ``index`` in one pass over its records."""

    jurisdictions: Counter[str] = Counter()
    sessions: Counter[str] = Counter()
    postings: Counter[int] = Counter()
    seen: set[str] = set()
    segments = 0
    tokens = 0
    total = 0
    for record in index:
        segments += 1
        tokens += record.token_count
        if record.key.slug not in seen:
            seen.add(record.key.slug)
            jurisdictions[record.key.jurisdiction] += 1
            sessions[record.key.session] += 1
        values = record.fingerprint_values
        total += len(values)
        postings.update(set(values))
    widest = max(postings.values(), default=0)
    return CorpusStats(
        instruments=len(seen),
        segments=segments,
        jurisdictions=dict(jurisdictions),
        sessions=dict(sessions),
        total_tokens=tokens,
        total_fingerprints=total,
        distinct_fingerprints=len(postings),
        widest_posting=widest,
        digest=index.config.digest(),
    )

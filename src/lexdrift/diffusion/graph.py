"""The reuse graph.

Nodes are instruments and an edge means "this much text of the target already
appears in the source". Edges are directed even though a match is symmetric,
because the question the graph exists to answer is which instrument came first.
Direction is assigned from enactment dates, not from the match, and an edge
between two instruments with no usable dates is recorded as undirected.

Counts accumulate, weight does not. Ten passages copied out of one act are one
edge with ten passages behind it, because the unit an analyst reasons about is
the pair of documents. The weight stays the strongest containment seen on the
pair, so it keeps its meaning as a fraction rather than growing past one every
time another passage is folded in.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass, field, replace
from datetime import date

from lexdrift.errors import MatchError
from lexdrift.identity import InstrumentKey

__all__ = ["ReuseEdge", "ReuseGraph"]


@dataclass(frozen=True, slots=True)
class ReuseEdge:
    """Accumulated reuse from ``source`` into ``target``."""

    source: InstrumentKey
    target: InstrumentKey
    passages: int = 0
    characters: int = 0
    weight: float = 0.0
    directed: bool = True

    @property
    def pair(self) -> tuple[str, str]:
        return (self.source.slug, self.target.slug)

    def merged(self, other: ReuseEdge) -> ReuseEdge:
        if self.pair != other.pair:
            raise MatchError("cannot merge edges between different pairs", pair=str(self.pair))
        return replace(
            self,
            passages=self.passages + other.passages,
            characters=self.characters + other.characters,
            weight=max(self.weight, other.weight),
            directed=self.directed and other.directed,
        )


@dataclass(slots=True)
class ReuseGraph:
    """A directed multigraph over instruments, collapsed to one edge per pair."""

    _edges: dict[tuple[str, str], ReuseEdge] = field(default_factory=dict)
    _dates: dict[str, date | None] = field(default_factory=dict)
    _keys: dict[str, InstrumentKey] = field(default_factory=dict)

    def __len__(self) -> int:
        return len(self._edges)

    def __iter__(self) -> Iterator[ReuseEdge]:
        return iter(sorted(self._edges.values(), key=lambda edge: edge.pair))

    def note(self, key: InstrumentKey, enacted: date | None = None) -> None:
        """Record an instrument and, when known, the date it took effect."""

        self._keys[key.slug] = key
        if enacted is not None or key.slug not in self._dates:
            self._dates[key.slug] = enacted

    def date_of(self, key: InstrumentKey) -> date | None:
        return self._dates.get(key.slug)

    def add(self, edge: ReuseEdge) -> ReuseEdge:
        """Add or merge one edge."""

        self.note(edge.source)
        self.note(edge.target)
        existing = self._edges.get(edge.pair)
        merged = edge if existing is None else existing.merged(edge)
        self._edges[edge.pair] = merged
        return merged

    def add_all(self, edges: Iterable[ReuseEdge]) -> int:
        count = 0
        for edge in edges:
            self.add(edge)
            count += 1
        return count

    @property
    def nodes(self) -> list[InstrumentKey]:
        return sorted(self._keys.values())

    def edges(self) -> list[ReuseEdge]:
        return list(self)

    def out_edges(self, key: InstrumentKey) -> list[ReuseEdge]:
        return [edge for edge in self if edge.source == key]

    def in_edges(self, key: InstrumentKey) -> list[ReuseEdge]:
        return [edge for edge in self if edge.target == key]

    def edge_between(self, source: InstrumentKey, target: InstrumentKey) -> ReuseEdge | None:
        return self._edges.get((source.slug, target.slug))

    def neighbours(self, key: InstrumentKey) -> set[InstrumentKey]:
        found: set[InstrumentKey] = set()
        for edge in self:
            if edge.source == key:
                found.add(edge.target)
            elif edge.target == key:
                found.add(edge.source)
        return found

    def total_weight(self) -> float:
        return sum(edge.weight for edge in self._edges.values())

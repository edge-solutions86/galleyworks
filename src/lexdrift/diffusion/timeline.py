"""Reading a family as a sequence, and noticing when it cannot be one.

A diffusion finding is a story about time: this text appeared here, then there,
then somewhere else. :func:`timeline` lays that out in order with the reuse each
step brought in.

:func:`find_cycles` is the check that the story is tellable. Direction comes from
enactment dates, so a directed cycle means two instruments each claim to precede
the other, which can only happen if a date is wrong or an edge was oriented from
something other than the calendar. It is rare and it is worth refusing to
present rather than drawing a picture with an arrow that loops.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from lexdrift.diffusion.graph import ReuseGraph
from lexdrift.diffusion.lineage import Lineage
from lexdrift.identity import InstrumentKey

__all__ = ["TimelineStep", "find_cycles", "timeline"]


@dataclass(frozen=True, slots=True)
class TimelineStep:
    """One instrument's place in a family's history."""

    position: int
    key: InstrumentKey
    when: date | None
    inherited_characters: int
    sources: tuple[InstrumentKey, ...]

    @property
    def is_origin(self) -> bool:
        return not self.sources

    @property
    def days_after(self) -> int | None:
        return None


def timeline(graph: ReuseGraph, lineage: Lineage) -> list[TimelineStep]:
    """Lay a family out in date order, earliest first.

    Undated members come last, in slug order, because putting them anywhere in
    the sequence would assert something the corpus does not know.
    """

    members = list(lineage.members)
    dated = [(graph.date_of(key), key) for key in members]
    known = sorted(
        ((when, key) for when, key in dated if when is not None),
        key=lambda pair: (pair[0], pair[1].slug),
    )
    unknown = sorted((key for when, key in dated if when is None), key=lambda key: key.slug)
    ordered: list[tuple[date | None, InstrumentKey]] = [*known, *((None, key) for key in unknown)]
    steps: list[TimelineStep] = []
    for position, (when, key) in enumerate(ordered):
        incoming = [edge for edge in graph.in_edges(key) if edge.source in set(members)]
        steps.append(
            TimelineStep(
                position=position,
                key=key,
                when=when,
                inherited_characters=sum(edge.characters for edge in incoming),
                sources=tuple(sorted(edge.source for edge in incoming)),
            )
        )
    return steps


def find_cycles(graph: ReuseGraph) -> list[tuple[str, ...]]:
    """Every directed cycle, each reported once from its lowest slug."""

    adjacency: dict[str, list[str]] = {node.slug: [] for node in graph.nodes}
    for edge in graph.edges():
        if edge.directed:
            adjacency.setdefault(edge.source.slug, []).append(edge.target.slug)
    found: set[tuple[str, ...]] = set()
    colour: dict[str, int] = dict.fromkeys(adjacency, 0)

    def walk(node: str, path: list[str]) -> None:
        colour[node] = 1
        path.append(node)
        for nxt in sorted(adjacency.get(node, ())):
            if colour.get(nxt, 0) == 1:
                start = path.index(nxt)
                cycle = path[start:]
                pivot = cycle.index(min(cycle))
                found.add(tuple(cycle[pivot:] + cycle[:pivot]))
            elif colour.get(nxt, 0) == 0:
                walk(nxt, path)
        path.pop()
        colour[node] = 2

    for node in sorted(adjacency):
        if colour.get(node, 0) == 0:
            walk(node, [])
    return sorted(found)

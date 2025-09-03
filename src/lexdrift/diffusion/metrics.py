"""Reading a reuse graph.

Two things an analyst asks of a family. First, which edges are worth drawing:
if A is copied into B and B into C, the A-to-C edge is usually implied rather
than observed, and a picture with all three is harder to read than one with two.
Transitive reduction removes the implied edge, but only when the dates make the
path chronological, because a shortcut that runs backwards in time is real
evidence and not a redundancy.

Second, how far the language travelled: how many jurisdictions, over how long,
and how much of the family's text the root accounts for.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.diffusion.lineage import Lineage
from lexdrift.identity import InstrumentKey

__all__ = ["DiffusionMetrics", "measure", "transitive_reduction"]


@dataclass(frozen=True, slots=True)
class DiffusionMetrics:
    """What one family looks like once it is measured."""

    members: int
    jurisdictions: int
    edges: int
    directed_edges: int
    characters: int
    first: date | None
    last: date | None

    @property
    def span(self) -> timedelta | None:
        if self.first is None or self.last is None:
            return None
        return self.last - self.first

    @property
    def span_days(self) -> int | None:
        span = self.span
        return None if span is None else span.days

    @property
    def is_single_jurisdiction(self) -> bool:
        return self.jurisdictions <= 1


def _reaches(
    graph: ReuseGraph, start: InstrumentKey, goal: InstrumentKey, *, skip: tuple[str, str]
) -> bool:
    """Is ``goal`` reachable from ``start`` without using the ``skip`` edge?"""

    stack = [start]
    seen = {start.slug}
    while stack:
        current = stack.pop()
        for edge in graph.out_edges(current):
            if not edge.directed or edge.pair == skip:
                continue
            if edge.target == goal:
                return True
            if edge.target.slug not in seen:
                seen.add(edge.target.slug)
                stack.append(edge.target)
    return False


def transitive_reduction(graph: ReuseGraph) -> ReuseGraph:
    """Drop directed edges that a longer directed path already implies."""

    reduced = ReuseGraph()
    for node in graph.nodes:
        reduced.note(node, graph.date_of(node))
    for edge in graph.edges():
        if edge.directed and _reaches(graph, edge.source, edge.target, skip=edge.pair):
            continue
        reduced.add(edge)
    return reduced


def measure(graph: ReuseGraph, lineage: Lineage) -> DiffusionMetrics:
    """Measure one family inside ``graph``."""

    members = set(lineage.members)
    edges: list[ReuseEdge] = [
        edge for edge in graph.edges() if edge.source in members and edge.target in members
    ]
    dates = [when for when in (graph.date_of(member) for member in members) if when is not None]
    return DiffusionMetrics(
        members=len(members),
        jurisdictions=len({member.jurisdiction for member in members}),
        edges=len(edges),
        directed_edges=sum(1 for edge in edges if edge.directed),
        characters=sum(edge.characters for edge in edges),
        first=min(dates) if dates else None,
        last=max(dates) if dates else None,
    )

"""Families of related instruments, and which one printed the text first.

A connected component of the reuse graph is a *family*: a set of instruments
that share text with each other, directly or through intermediaries. The root of
a family is the earliest dated member, which is the best available candidate for
where the language originated.

"Best available" is doing real work in that sentence. The earliest instrument in
the corpus is only the earliest one that was indexed, and a model bill drafted
by an association that never appears in any legislature will never be the root.
:attr:`Lineage.confident` says whether the dates support the claim at all.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from datetime import date

from lexdrift.diffusion.graph import ReuseGraph
from lexdrift.identity import InstrumentKey

__all__ = ["Lineage", "components", "earliest", "lineages"]


@dataclass(frozen=True, slots=True)
class Lineage:
    """One family of instruments sharing text."""

    root: InstrumentKey | None
    members: tuple[InstrumentKey, ...]
    root_date: date | None = None
    dated_members: int = 0

    @property
    def size(self) -> int:
        return len(self.members)

    @property
    def jurisdictions(self) -> tuple[str, ...]:
        return tuple(sorted({member.jurisdiction for member in self.members}))

    @property
    def spreads(self) -> bool:
        """True when the family crosses more than one jurisdiction."""

        return len(self.jurisdictions) > 1

    @property
    def confident(self) -> bool:
        """True only when every member is dated, so the root is not a guess."""

        return self.root is not None and self.dated_members == self.size


def components(graph: ReuseGraph) -> list[list[InstrumentKey]]:
    """Connected components of the graph, ignoring edge direction."""

    seen: set[str] = set()
    found: list[list[InstrumentKey]] = []
    for node in graph.nodes:
        if node.slug in seen:
            continue
        stack = [node]
        member: list[InstrumentKey] = []
        seen.add(node.slug)
        while stack:
            current = stack.pop()
            member.append(current)
            for neighbour in graph.neighbours(current):
                if neighbour.slug not in seen:
                    seen.add(neighbour.slug)
                    stack.append(neighbour)
        found.append(sorted(member))
    found.sort(key=lambda group: (-len(group), group[0].slug))
    return found


def earliest(graph: ReuseGraph, members: Iterable[InstrumentKey]) -> InstrumentKey | None:
    """The earliest dated instrument among ``members``, if any is dated."""

    dated = [(graph.date_of(member), member) for member in members]
    known = [(when, member) for when, member in dated if when is not None]
    if not known:
        return None
    known.sort(key=lambda pair: (pair[0], pair[1].slug))
    return known[0][1]


def lineages(graph: ReuseGraph) -> list[Lineage]:
    """Split the graph into families, each with its earliest member as root."""

    found: list[Lineage] = []
    for group in components(graph):
        root = earliest(graph, group)
        dated = sum(1 for member in group if graph.date_of(member) is not None)
        found.append(
            Lineage(
                root=root,
                members=tuple(group),
                root_date=None if root is None else graph.date_of(root),
                dated_members=dated,
            )
        )
    return found

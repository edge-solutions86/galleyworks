from __future__ import annotations

from datetime import date

import pytest

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.errors import MatchError
from lexdrift.identity import Chamber, InstrumentKey


def key(jurisdiction: str, number: str = "HB0001") -> InstrumentKey:
    return InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number)


WY = key("wy", "HB0142")
MT = key("mt", "HB0311")
ND = key("nd", "HB0044")


def edge(source: InstrumentKey, target: InstrumentKey, **kwargs: object) -> ReuseEdge:
    base: dict[str, object] = {"passages": 1, "characters": 200, "weight": 0.5}
    base.update(kwargs)
    return ReuseEdge(source=source, target=target, **base)  # type: ignore[arg-type]


def test_an_empty_graph() -> None:
    graph = ReuseGraph()
    assert len(graph) == 0
    assert graph.nodes == []
    assert graph.total_weight() == 0.0


def test_adding_an_edge_registers_both_nodes() -> None:
    graph = ReuseGraph()
    graph.add(edge(WY, MT))
    assert graph.nodes == sorted([WY, MT])
    assert len(graph) == 1


def test_edges_between_the_same_pair_merge() -> None:
    graph = ReuseGraph()
    graph.add(edge(WY, MT))
    graph.add(edge(WY, MT, passages=3, characters=600, weight=1.5))
    merged = graph.edge_between(WY, MT)
    assert merged is not None
    assert merged.passages == 4
    assert merged.characters == 800
    assert merged.weight == pytest.approx(2.0)


def test_the_two_directions_are_separate_edges() -> None:
    graph = ReuseGraph()
    graph.add(edge(WY, MT))
    graph.add(edge(MT, WY))
    assert len(graph) == 2


def test_merging_unrelated_edges_is_rejected() -> None:
    with pytest.raises(MatchError, match="different pairs"):
        edge(WY, MT).merged(edge(MT, ND))


def test_merging_keeps_undirected_if_either_side_is() -> None:
    merged = edge(WY, MT).merged(edge(WY, MT, directed=False))
    assert not merged.directed


def test_out_and_in_edges() -> None:
    graph = ReuseGraph()
    graph.add(edge(WY, MT))
    graph.add(edge(WY, ND))
    graph.add(edge(ND, MT))
    assert [e.target for e in graph.out_edges(WY)] == sorted([MT, ND])
    assert [e.source for e in graph.in_edges(MT)] == sorted([WY, ND])


def test_neighbours_ignore_direction() -> None:
    graph = ReuseGraph()
    graph.add(edge(WY, MT))
    graph.add(edge(ND, WY))
    assert graph.neighbours(WY) == {MT, ND}


def test_an_absent_edge_is_none() -> None:
    assert ReuseGraph().edge_between(WY, MT) is None


def test_dates_are_recorded_and_kept() -> None:
    graph = ReuseGraph()
    graph.note(WY, date(2025, 3, 4))
    graph.note(WY)
    assert graph.date_of(WY) == date(2025, 3, 4)
    assert graph.date_of(MT) is None


def test_adding_an_edge_does_not_erase_a_known_date() -> None:
    graph = ReuseGraph()
    graph.note(WY, date(2025, 3, 4))
    graph.add(edge(WY, MT))
    assert graph.date_of(WY) == date(2025, 3, 4)


def test_iteration_is_ordered_by_pair() -> None:
    graph = ReuseGraph()
    graph.add(edge(WY, MT))
    graph.add(edge(MT, ND))
    assert [e.pair for e in graph] == sorted(e.pair for e in graph)


def test_add_all_counts_what_it_added() -> None:
    graph = ReuseGraph()
    assert graph.add_all([edge(WY, MT), edge(MT, ND)]) == 2
    assert graph.total_weight() == pytest.approx(1.0)

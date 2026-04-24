from __future__ import annotations

from datetime import date

from golden_corpus import golden_instruments
from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.diffusion.lineage import lineages
from lexdrift.diffusion.timeline import find_cycles, timeline
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.pipeline import Engine


def key(jurisdiction: str, number: str = "HB0001") -> InstrumentKey:
    return InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number)


A = key("wy", "HB0142")
B = key("mt", "HB0311")
C = key("nd", "HB0044")


def link(source: InstrumentKey, target: InstrumentKey, *, directed: bool = True) -> ReuseEdge:
    return ReuseEdge(
        source=source, target=target, passages=1, characters=200, weight=0.8, directed=directed
    )


def chain() -> ReuseGraph:
    graph = ReuseGraph()
    graph.note(A, date(2023, 1, 1))
    graph.note(B, date(2024, 1, 1))
    graph.note(C, date(2025, 1, 1))
    graph.add(link(A, B))
    graph.add(link(B, C))
    return graph


def test_a_timeline_runs_in_date_order() -> None:
    graph = chain()
    steps = timeline(graph, lineages(graph)[0])
    assert [step.key for step in steps] == [A, B, C]
    assert [step.position for step in steps] == [0, 1, 2]


def test_days_after_measures_from_the_origin() -> None:
    graph = chain()
    steps = timeline(graph, lineages(graph)[0])
    assert steps[0].days_after(steps[0]) == 0
    assert steps[1].days_after(steps[0]) == 365


def test_days_after_is_none_without_dates() -> None:
    graph = ReuseGraph()
    graph.add(link(A, B))
    steps = timeline(graph, lineages(graph)[0])
    assert steps[0].days_after(steps[1]) is None


def test_the_first_step_is_the_origin() -> None:
    graph = chain()
    steps = timeline(graph, lineages(graph)[0])
    assert steps[0].is_origin
    assert steps[0].sources == ()
    assert not steps[1].is_origin


def test_later_steps_name_their_sources() -> None:
    graph = chain()
    steps = timeline(graph, lineages(graph)[0])
    assert steps[1].sources == (A,)
    assert steps[1].inherited_characters == 200


def test_undated_members_come_last() -> None:
    graph = chain()
    stray = key("co", "SB0007")
    graph.add(link(C, stray))
    steps = timeline(graph, lineages(graph)[0])
    assert steps[-1].key == stray
    assert steps[-1].when is None


def test_a_wholly_undated_family_is_slug_ordered() -> None:
    graph = ReuseGraph()
    graph.add(link(B, A))
    steps = timeline(graph, lineages(graph)[0])
    assert [step.key.slug for step in steps] == sorted(step.key.slug for step in steps)


def test_an_acyclic_graph_has_no_cycles() -> None:
    assert find_cycles(chain()) == []


def test_a_two_node_cycle_is_found() -> None:
    graph = chain()
    graph.add(link(C, A))
    cycles = find_cycles(graph)
    assert cycles
    assert set(cycles[0]) == {A.slug, B.slug, C.slug}


def test_a_cycle_is_reported_once() -> None:
    graph = ReuseGraph()
    graph.add(link(A, B))
    graph.add(link(B, A))
    assert len(find_cycles(graph)) == 1


def test_an_undirected_edge_never_makes_a_cycle() -> None:
    graph = ReuseGraph()
    graph.add(link(A, B, directed=False))
    graph.add(link(B, A, directed=False))
    assert find_cycles(graph) == []


def test_an_empty_graph_has_no_cycles() -> None:
    assert find_cycles(ReuseGraph()) == []


def test_the_golden_corpus_is_acyclic_and_ordered() -> None:
    result = Engine().run_over(golden_instruments())
    assert find_cycles(result.graph) == []
    family = next(family for family in lineages(result.graph) if family.size > 1)
    steps = timeline(result.graph, family)
    dates = [step.when for step in steps if step.when is not None]
    assert dates == sorted(dates)
    assert steps[0].key.jurisdiction == "wy"

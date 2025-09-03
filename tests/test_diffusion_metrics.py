from __future__ import annotations

from datetime import date

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.diffusion.lineage import lineages
from lexdrift.diffusion.metrics import measure, transitive_reduction
from lexdrift.identity import Chamber, InstrumentKey


def key(jurisdiction: str, number: str = "HB0001") -> InstrumentKey:
    return InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number)


A = key("wy", "HB0142")
B = key("mt", "HB0311")
C = key("nd", "HB0044")


def link(
    source: InstrumentKey, target: InstrumentKey, *, directed: bool = True, characters: int = 100
) -> ReuseEdge:
    return ReuseEdge(
        source=source,
        target=target,
        passages=1,
        characters=characters,
        weight=0.7,
        directed=directed,
    )


def chain() -> ReuseGraph:
    graph = ReuseGraph()
    graph.note(A, date(2023, 1, 1))
    graph.note(B, date(2024, 1, 1))
    graph.note(C, date(2025, 1, 1))
    graph.add(link(A, B))
    graph.add(link(B, C))
    graph.add(link(A, C))
    return graph


def test_a_shortcut_edge_is_removed() -> None:
    reduced = transitive_reduction(chain())
    assert {edge.pair for edge in reduced} == {(A.slug, B.slug), (B.slug, C.slug)}


def test_reduction_keeps_the_nodes_and_their_dates() -> None:
    reduced = transitive_reduction(chain())
    assert reduced.nodes == chain().nodes
    assert reduced.date_of(A) == date(2023, 1, 1)


def test_an_undirected_edge_is_never_removed() -> None:
    graph = chain()
    graph.add(link(A, C, directed=False))
    reduced = transitive_reduction(graph)
    assert reduced.edge_between(A, C) is not None


def test_a_reduction_with_nothing_to_remove() -> None:
    graph = ReuseGraph()
    graph.add(link(A, B))
    graph.add(link(A, C))
    assert len(transitive_reduction(graph)) == 2


def test_reduction_of_an_empty_graph() -> None:
    assert len(transitive_reduction(ReuseGraph())) == 0


def test_metrics_count_members_and_jurisdictions() -> None:
    graph = chain()
    metrics = measure(graph, lineages(graph)[0])
    assert metrics.members == 3
    assert metrics.jurisdictions == 3
    assert metrics.edges == 3
    assert metrics.directed_edges == 3
    assert not metrics.is_single_jurisdiction


def test_metrics_sum_the_reused_characters() -> None:
    graph = chain()
    metrics = measure(graph, lineages(graph)[0])
    assert metrics.characters == 300


def test_metrics_span_the_dates() -> None:
    graph = chain()
    metrics = measure(graph, lineages(graph)[0])
    assert metrics.first == date(2023, 1, 1)
    assert metrics.last == date(2025, 1, 1)
    assert metrics.span_days == 731


def test_metrics_on_an_undated_family() -> None:
    graph = ReuseGraph()
    graph.add(link(A, B))
    metrics = measure(graph, lineages(graph)[0])
    assert metrics.first is None
    assert metrics.span is None
    assert metrics.span_days is None


def test_a_single_jurisdiction_family_is_flagged() -> None:
    graph = ReuseGraph()
    graph.add(link(key("wy", "HB0001"), key("wy", "HB0002")))
    metrics = measure(graph, lineages(graph)[0])
    assert metrics.is_single_jurisdiction


def test_metrics_ignore_edges_outside_the_family() -> None:
    graph = chain()
    other = key("ut", "HB0500")
    graph.add(link(other, key("ut", "HB0501")))
    families = sorted(lineages(graph), key=lambda item: -item.size)
    metrics = measure(graph, families[0])
    assert metrics.members == 3
    assert metrics.edges == 3

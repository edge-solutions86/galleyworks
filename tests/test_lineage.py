from __future__ import annotations

from datetime import date

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.diffusion.lineage import components, earliest, lineages
from lexdrift.identity import Chamber, InstrumentKey


def key(jurisdiction: str, number: str = "HB0001") -> InstrumentKey:
    return InstrumentKey(jurisdiction, "2025", Chamber.LOWER, number)


WY = key("wy", "HB0142")
MT = key("mt", "HB0311")
ND = key("nd", "HB0044")
CO = key("co", "SB0007")


def graph_with(*pairs: tuple[InstrumentKey, InstrumentKey], **dates: date) -> ReuseGraph:
    graph = ReuseGraph()
    for source, target in pairs:
        graph.add(ReuseEdge(source=source, target=target, passages=1, characters=100, weight=0.8))
    for slug, when in dates.items():
        graph.note(key(slug[:2], slug[2:] or "HB0001"), when)
    return graph


def test_an_empty_graph_has_no_families() -> None:
    assert components(ReuseGraph()) == []
    assert lineages(ReuseGraph()) == []


def test_one_edge_is_one_family() -> None:
    graph = graph_with((WY, MT))
    assert components(graph) == [sorted([WY, MT])]


def test_a_chain_is_one_family() -> None:
    graph = graph_with((WY, MT), (MT, ND))
    assert len(components(graph)) == 1
    assert len(components(graph)[0]) == 3


def test_disconnected_pairs_are_separate_families() -> None:
    graph = graph_with((WY, MT), (ND, CO))
    assert len(components(graph)) == 2


def test_families_come_back_largest_first() -> None:
    graph = graph_with((WY, MT), (MT, ND), (CO, key("ut")))
    sizes = [len(group) for group in components(graph)]
    assert sizes == sorted(sizes, reverse=True)


def test_the_earliest_dated_member_is_the_root() -> None:
    graph = graph_with((WY, MT), (MT, ND))
    graph.note(WY, date(2023, 5, 1))
    graph.note(MT, date(2024, 5, 1))
    graph.note(ND, date(2025, 5, 1))
    assert earliest(graph, [MT, ND, WY]) == WY


def test_an_undated_family_has_no_root() -> None:
    graph = graph_with((WY, MT))
    assert earliest(graph, [WY, MT]) is None
    assert lineages(graph)[0].root is None


def test_a_partly_dated_family_is_not_confident() -> None:
    graph = graph_with((WY, MT))
    graph.note(WY, date(2023, 5, 1))
    lineage = lineages(graph)[0]
    assert lineage.root == WY
    assert lineage.dated_members == 1
    assert not lineage.confident


def test_a_fully_dated_family_is_confident() -> None:
    graph = graph_with((WY, MT))
    graph.note(WY, date(2023, 5, 1))
    graph.note(MT, date(2024, 5, 1))
    lineage = lineages(graph)[0]
    assert lineage.confident
    assert lineage.root_date == date(2023, 5, 1)


def test_a_tie_on_dates_breaks_on_the_slug() -> None:
    graph = graph_with((WY, MT))
    graph.note(WY, date(2024, 1, 1))
    graph.note(MT, date(2024, 1, 1))
    assert earliest(graph, [WY, MT]) == min(WY, MT, key=lambda item: item.slug)


def test_a_family_reports_its_jurisdictions() -> None:
    graph = graph_with((WY, MT), (MT, ND))
    lineage = lineages(graph)[0]
    assert lineage.jurisdictions == ("mt", "nd", "wy")
    assert lineage.spreads
    assert lineage.size == 3


def test_a_single_jurisdiction_family_does_not_spread() -> None:
    graph = graph_with((key("wy", "HB0001"), key("wy", "HB0002")))
    assert not lineages(graph)[0].spreads

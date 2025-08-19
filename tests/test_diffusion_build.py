from __future__ import annotations

from datetime import date

from conftest_corpus import MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.diffusion.build import build_graph, orient
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.engine import Matcher

WY = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")
MT = InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311")


def test_the_earlier_instrument_is_the_source() -> None:
    dates = {WY.slug: date(2024, 3, 1), MT.slug: date(2025, 3, 1)}
    assert orient(MT, WY, dates) == (WY, MT, True)
    assert orient(WY, MT, dates) == (WY, MT, True)


def test_a_missing_date_leaves_the_edge_undirected() -> None:
    source, target, directed = orient(MT, WY, {WY.slug: date(2024, 3, 1)})
    assert not directed
    assert (source, target) == (MT, WY)


def test_equal_dates_leave_the_edge_undirected() -> None:
    dates = {WY.slug: date(2025, 3, 1), MT.slug: date(2025, 3, 1)}
    _, _, directed = orient(WY, MT, dates)
    assert not directed


def test_undirected_orientation_is_stable() -> None:
    assert orient(WY, MT, {}) == orient(MT, WY, {})


def test_building_from_a_real_match() -> None:
    earlier = make_bill(
        jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE, enacted=date(2024, 7, 1)
    )
    later = make_bill(
        jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE, enacted=date(2025, 7, 1)
    )
    index = MemoryIndex()
    index.add_all(fingerprints(earlier))
    matches = Matcher(index).match_instrument(later)
    graph = build_graph(matches, [earlier, later])
    assert len(graph) >= 1
    edge = graph.edges()[0]
    assert edge.source.jurisdiction == "wy"
    assert edge.target.jurisdiction == "mt"
    assert edge.directed


def test_the_graph_records_dates() -> None:
    earlier = make_bill(
        jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE, enacted=date(2024, 7, 1)
    )
    graph = build_graph([], [earlier])
    assert graph.date_of(earlier.key) == date(2024, 7, 1)


def test_edges_carry_the_reused_characters() -> None:
    earlier = make_bill(
        jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE, enacted=date(2024, 7, 1)
    )
    later = make_bill(
        jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE, enacted=date(2025, 7, 1)
    )
    index = MemoryIndex()
    index.add_all(fingerprints(earlier))
    graph = build_graph(Matcher(index).match_instrument(later), [earlier, later])
    edge = graph.edges()[0]
    assert edge.characters > 0
    assert edge.passages > 0
    assert 0.0 < edge.weight <= 1.0


def test_several_matches_on_one_pair_collapse_to_one_edge() -> None:
    earlier = make_bill(
        jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE, enacted=date(2024, 7, 1)
    )
    later = make_bill(
        jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE, enacted=date(2025, 7, 1)
    )
    index = MemoryIndex()
    index.add_all(fingerprints(earlier))
    matches = Matcher(index).match_instrument(later)
    graph = build_graph(matches, [earlier, later])
    assert len(graph) == 1
    assert graph.edges()[0].passages >= len(matches)


def test_building_from_nothing() -> None:
    graph = build_graph([], [])
    assert len(graph) == 0
    assert graph.nodes == []


def test_undated_instruments_give_an_undirected_edge() -> None:
    earlier = make_bill(jurisdiction="wy", number="HB0142", body=MODEL_CLAUSE)
    later = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    index = MemoryIndex()
    index.add_all(fingerprints(earlier))
    graph = build_graph(Matcher(index).match_instrument(later), [earlier, later])
    assert not graph.edges()[0].directed

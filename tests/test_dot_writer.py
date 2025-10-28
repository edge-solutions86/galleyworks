from __future__ import annotations

import io
from datetime import date

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.report.dot_writer import node_id, to_dot, write_dot

WY = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")
MT = InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311")
MT2 = InstrumentKey("mt", "2025", Chamber.UPPER, "SB0044")


def graph(*, directed: bool = True, dated: bool = True) -> ReuseGraph:
    built = ReuseGraph()
    built.add(
        ReuseEdge(
            source=WY,
            target=MT,
            passages=3,
            characters=940,
            weight=0.75,
            directed=directed,
        )
    )
    built.note(MT2)
    if dated:
        built.note(WY, date(2024, 7, 1))
        built.note(MT, date(2025, 7, 1))
    return built


def test_the_document_is_a_digraph() -> None:
    rendered = to_dot(graph())
    assert rendered.startswith("digraph reuse {")
    assert rendered.rstrip().endswith("}")


def test_the_graph_can_be_named() -> None:
    assert to_dot(graph(), name="diffusion").startswith("digraph diffusion {")


def test_node_ids_are_quoted() -> None:
    assert node_id(WY) == '"wy/2025/lower/HB0142"'


def test_every_node_appears() -> None:
    rendered = to_dot(graph())
    for key in (WY, MT, MT2):
        assert node_id(key) in rendered


def test_nodes_are_grouped_by_jurisdiction() -> None:
    rendered = to_dot(graph())
    assert "subgraph cluster_wy {" in rendered
    assert "subgraph cluster_mt {" in rendered


def test_node_labels_carry_the_number_and_date() -> None:
    rendered = to_dot(graph())
    assert 'label="HB0142\\n2024-07-01"' in rendered


def test_an_undated_node_has_no_date_in_its_label() -> None:
    rendered = to_dot(graph(dated=False))
    assert 'label="HB0142"' in rendered


def test_edges_carry_their_size() -> None:
    rendered = to_dot(graph())
    assert 'label="940 chars / 3p"' in rendered


def test_a_directed_edge_is_solid() -> None:
    rendered = to_dot(graph())
    assert "style=solid" in rendered
    assert "arrowhead=normal" in rendered


def test_an_undirected_edge_is_dashed() -> None:
    rendered = to_dot(graph(directed=False))
    assert "style=dashed" in rendered
    assert "arrowhead=none" in rendered


def test_pen_width_follows_the_weight() -> None:
    light = ReuseGraph()
    light.add(ReuseEdge(source=WY, target=MT, weight=0.0))
    heavy = ReuseGraph()
    heavy.add(ReuseEdge(source=WY, target=MT, weight=1.0))
    assert "penwidth=1.0" in to_dot(light)
    assert "penwidth=6.0" in to_dot(heavy)


def test_an_empty_graph_renders() -> None:
    rendered = to_dot(ReuseGraph())
    assert rendered.startswith("digraph reuse {")
    assert "->" not in rendered


def test_write_dot_sends_it_to_a_stream() -> None:
    stream = io.StringIO()
    write_dot(graph(), stream)
    assert stream.getvalue() == to_dot(graph())

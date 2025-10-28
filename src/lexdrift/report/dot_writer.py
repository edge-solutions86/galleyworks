"""Graphviz DOT output for a reuse graph.

A diffusion finding is a shape before it is a table: one act at the top and a
fan of later ones below it reads instantly, and the same information as forty
rows of CSV does not.

Nodes are grouped into a subgraph per jurisdiction, because the question the
picture answers is almost always "where did this cross a state line". Undirected
edges are drawn dashed, so an edge the dates could not orient is visibly a
weaker claim than one they could.
"""

from __future__ import annotations

from collections import defaultdict
from typing import TextIO

from lexdrift.diffusion.graph import ReuseEdge, ReuseGraph
from lexdrift.identity import InstrumentKey

__all__ = ["node_id", "to_dot", "write_dot"]

_MIN_PEN = 1.0
_MAX_PEN = 6.0


def node_id(key: InstrumentKey) -> str:
    """A DOT-safe identifier for an instrument."""

    return '"' + key.slug.replace('"', "'") + '"'


def _pen_width(edge: ReuseEdge) -> float:
    weight = max(0.0, min(1.0, edge.weight))
    return round(_MIN_PEN + weight * (_MAX_PEN - _MIN_PEN), 2)


def _label(edge: ReuseEdge) -> str:
    return f"{edge.characters} chars / {edge.passages}p"


def to_dot(graph: ReuseGraph, *, name: str = "reuse") -> str:
    """Render the graph as DOT source."""

    lines = [
        f"digraph {name} {{",
        "  rankdir=TB;",
        '  node [shape=box, fontname="Helvetica", fontsize=10];',
        '  edge [fontname="Helvetica", fontsize=8];',
    ]
    grouped: defaultdict[str, list[InstrumentKey]] = defaultdict(list)
    for node in graph.nodes:
        grouped[node.jurisdiction].append(node)
    for jurisdiction in sorted(grouped):
        lines.append(f"  subgraph cluster_{jurisdiction} {{")
        lines.append(f'    label="{jurisdiction}";')
        lines.append("    style=dotted;")
        for node in grouped[jurisdiction]:
            when = graph.date_of(node)
            suffix = "" if when is None else f"\\n{when.isoformat()}"
            lines.append(f'    {node_id(node)} [label="{node.number}{suffix}"];')
        lines.append("  }")
    for edge in graph.edges():
        style = "solid" if edge.directed else "dashed"
        arrow = "normal" if edge.directed else "none"
        lines.append(
            f"  {node_id(edge.source)} -> {node_id(edge.target)} "
            f'[label="{_label(edge)}", penwidth={_pen_width(edge)}, '
            f"style={style}, arrowhead={arrow}];"
        )
    lines.append("}")
    return "\n".join(lines) + "\n"


def write_dot(graph: ReuseGraph, stream: TextIO, *, name: str = "reuse") -> None:
    """Write DOT source to ``stream``."""

    stream.write(to_dot(graph, name=name))

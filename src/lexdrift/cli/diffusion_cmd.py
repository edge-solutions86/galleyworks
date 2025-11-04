"""``lexdrift diffusion`` - which families exist and where each one started.

Where ``report`` answers "what does this bill reuse", this answers "what has
travelled, and how far". It runs the same forward pass and then reads the graph
rather than the individual reports.

Families of one are dropped unless asked for. An instrument with no reuse at all
is its own family in the graph sense, and printing several hundred of them
buries the two that matter.
"""

from __future__ import annotations

from pathlib import Path

import click

from lexdrift.cli.common import build_engine, engine_options
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.diffusion.lineage import Lineage, lineages
from lexdrift.diffusion.metrics import DiffusionMetrics, measure, transitive_reduction
from lexdrift.report.dot_writer import write_dot

__all__ = ["describe", "diffusion"]


def describe(lineage: Lineage, metrics: DiffusionMetrics) -> list[str]:
    """One block of text about one family."""

    root = "unknown" if lineage.root is None else lineage.root.slug
    confidence = "dated" if lineage.confident else "partly dated"
    lines = [
        f"family of {lineage.size} across {', '.join(lineage.jurisdictions)}",
        f"  earliest: {root} ({confidence})",
    ]
    if metrics.span_days is not None:
        lines.append(
            f"  spread over {metrics.span_days} days, {metrics.characters} characters "
            f"across {metrics.edges} edges"
        )
    else:
        lines.append(f"  {metrics.characters} characters across {metrics.edges} edges, no dates")
    for member in lineage.members:
        marker = "*" if lineage.root is not None and member == lineage.root else " "
        lines.append(f"   {marker} {member.slug}")
    return lines


@cli.command(name="diffusion", short_help="report which families of instruments share text")
@click.argument("source")
@engine_options()
@click.option(
    "--min-members", type=int, default=2, show_default=True, help="ignore smaller families"
)
@click.option("--crossing-only", is_flag=True, help="only families that cross a jurisdiction")
@click.option("--reduce", "reduce_edges", is_flag=True, help="drop edges a longer path implies")
@click.option(
    "--dot",
    "dot_path",
    type=click.Path(dir_okay=False),
    default=None,
    help="also write the graph as graphviz dot",
)
@click.pass_obj
def diffusion(
    runtime: Runtime,
    source: str,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    min_members: int,
    crossing_only: bool,
    reduce_edges: bool,
    dot_path: str | None,
) -> int:
    """Report the families of instruments sharing text in SOURCE."""

    engine = build_engine(index_path, config_path, profile)
    result = engine.run(load_corpus(source))
    graph = transitive_reduction(result.graph) if reduce_edges else result.graph
    families = [
        lineage
        for lineage in lineages(graph)
        if lineage.size >= min_members and (not crossing_only or lineage.spreads)
    ]
    for lineage in families:
        for line in describe(lineage, measure(graph, lineage)):
            runtime.line(line)
        runtime.line()
    runtime.line(f"{len(families)} families over {len(graph)} edges")
    if dot_path is not None:
        with Path(dot_path).open("w", encoding="utf-8", newline="\n") as stream:
            write_dot(graph, stream)
        runtime.line(f"graph written to {dot_path}")
    return EXIT_FINDINGS if families else EXIT_OK

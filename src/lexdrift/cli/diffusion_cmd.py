"""``lexdrift diffusion`` - which families exist and where each one started.

Where ``report`` answers "what does this bill reuse", this answers "what has
travelled, and how far". It runs the same forward pass and then reads the graph
rather than the individual reports.

Families of one are dropped unless asked for. An instrument with no reuse at all
is its own family in the graph sense, and printing several hundred of them
buries the two that matter.
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import TextIO

from lexdrift.cli.common import add_engine_options, engine_from
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Command, register
from lexdrift.diffusion.lineage import Lineage, lineages
from lexdrift.diffusion.metrics import measure, transitive_reduction
from lexdrift.report.dot_writer import write_dot

__all__ = ["configure", "describe", "handle"]


def configure(parser: argparse.ArgumentParser) -> None:
    add_engine_options(parser)
    parser.add_argument("source", help="a corpus directory or a manifest.yaml")
    parser.add_argument(
        "--min-members",
        type=int,
        default=2,
        help="ignore families smaller than this",
    )
    parser.add_argument(
        "--crossing-only",
        action="store_true",
        help="only report families that cross a jurisdiction",
    )
    parser.add_argument(
        "--reduce",
        action="store_true",
        help="drop edges a longer chronological path already implies",
    )
    parser.add_argument(
        "--dot",
        dest="dot_path",
        default=None,
        help="also write the graph as graphviz dot",
    )


def describe(lineage: Lineage, metrics: object) -> list[str]:
    """One block of text about one family."""

    root = "unknown" if lineage.root is None else lineage.root.slug
    confidence = "dated" if lineage.confident else "partly dated"
    lines = [
        f"family of {lineage.size} across {', '.join(lineage.jurisdictions)}",
        f"  earliest: {root} ({confidence})",
    ]
    span = getattr(metrics, "span_days", None)
    characters = getattr(metrics, "characters", 0)
    edges = getattr(metrics, "edges", 0)
    if span is not None:
        lines.append(f"  spread over {span} days, {characters} characters across {edges} edges")
    else:
        lines.append(f"  {characters} characters across {edges} edges, no dates")
    for member in lineage.members:
        marker = "*" if lineage.root is not None and member == lineage.root else " "
        lines.append(f"   {marker} {member.slug}")
    return lines


def handle(args: argparse.Namespace, out: TextIO) -> int:
    engine = engine_from(args)
    result = engine.run(load_corpus(args.source))
    graph = transitive_reduction(result.graph) if args.reduce else result.graph
    families = [
        lineage
        for lineage in lineages(graph)
        if lineage.size >= args.min_members and (not args.crossing_only or lineage.spreads)
    ]
    for lineage in families:
        for line in describe(lineage, measure(graph, lineage)):
            print(line, file=out)
        print("", file=out)
    print(f"{len(families)} families over {len(graph)} edges", file=out)
    if args.dot_path is not None:
        with Path(args.dot_path).open("w", encoding="utf-8", newline="\n") as stream:
            write_dot(graph, stream)
        print(f"graph written to {args.dot_path}", file=out)
    return EXIT_FINDINGS if families else EXIT_OK


register(
    Command(
        name="diffusion",
        help="report which families of instruments share text",
        configure=configure,
        handler=handle,
    )
)

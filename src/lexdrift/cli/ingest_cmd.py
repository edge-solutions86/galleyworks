"""``lexdrift ingest`` - parse a corpus and report what came back.

It writes nothing and indexes nothing. Its job is to answer "does this directory
parse", which is the first question about any new corpus and the one that is
most annoying to answer by running the whole pipeline.
"""

from __future__ import annotations

import click

from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.corpus import Corpus, discover, load_manifest
from lexdrift.errors import IngestError
from lexdrift.ingest.registry import default_registry

__all__ = ["ingest", "load_corpus"]


def load_corpus(source: str) -> Corpus:
    """Load ``source`` as a manifest when it looks like one, else as a directory."""

    if source.endswith((".yaml", ".yml")):
        return load_manifest(source)
    return discover(source)


@cli.command(name="ingest", short_help="parse a corpus and report what came back")
@click.argument("source")
@click.option("--format", "format_name", default=None, help="pin one reader for every document")
@click.option("--quiet", is_flag=True, help="print only the totals")
@click.pass_obj
def ingest(runtime: Runtime, source: str, format_name: str | None, quiet: bool) -> int:
    """Parse every document under SOURCE without indexing anything."""

    corpus = load_corpus(source)
    registry = default_registry()
    parsed = 0
    failed = 0
    for entry in corpus:
        try:
            instrument = registry.read(entry.source(), format_name=format_name or entry.format_name)
        except IngestError as exc:
            failed += 1
            runtime.line(f"failed  {entry.path}: {exc}")
            continue
        parsed += 1
        if not quiet:
            segments = sum(1 for _ in instrument.body_segments())
            runtime.line(
                f"parsed  {instrument.key.slug}  {instrument.source_format}  {segments} segments"
            )
    for path in corpus.skipped:
        runtime.line(f"skipped {path}: file name does not name an instrument")
    runtime.line(f"{parsed} parsed, {failed} failed, {len(corpus.skipped)} skipped")
    return EXIT_FINDINGS if failed else EXIT_OK

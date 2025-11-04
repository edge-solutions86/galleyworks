"""``lexdrift index`` - build or extend an index from a corpus.

Indexing is separate from querying because it is the expensive half and it is
run on a schedule, while a query is run by a person waiting for an answer. The
two commands share the ``--index`` flag and nothing else.
"""

from __future__ import annotations

import click

from lexdrift.cli.common import build_engine, engine_options
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_OK, Runtime, cli
from lexdrift.corpus import read_all

__all__ = ["index"]


@cli.command(name="index", short_help="build or extend an index from a corpus")
@click.argument("source")
@engine_options()
@click.option("--replace", is_flag=True, help="drop each instrument before adding it again")
@click.option("--stats", is_flag=True, help="print index statistics when the run finishes")
@click.pass_obj
def index(
    runtime: Runtime,
    source: str,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    replace: bool,
    stats: bool,
) -> int:
    """Index every document under SOURCE."""

    engine = build_engine(index_path, config_path, profile)
    corpus = load_corpus(source)
    added = 0
    documents = 0
    for instrument in read_all(corpus, engine.registry):
        if replace:
            engine.index.remove_instrument(instrument.key)
        written = engine.add(instrument)
        added += written
        documents += 1
        runtime.line(f"indexed {instrument.key.slug}  {written} segments")
    runtime.line(f"{documents} instruments, {added} segments indexed")
    if stats:
        summary = engine.index.stats()
        runtime.line(
            f"index now holds {summary.instruments} instruments, {summary.segments} segments, "
            f"{summary.buckets} buckets, {summary.postings} distinct fingerprints"
        )
        runtime.line(f"engine digest {summary.digest}")
    return EXIT_OK

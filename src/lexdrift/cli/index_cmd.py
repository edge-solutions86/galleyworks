"""``lexdrift index`` - build or extend an index from a corpus.

Indexing is separate from querying because it is the expensive half and it is
run on a schedule, while a query is run by a person waiting for an answer. The
two commands share the ``--index`` flag and nothing else.

``--incremental`` compares each document against the digest the index recorded
and writes only what moved. It needs a durable index: an in-memory one has
nothing to compare against, so the flag is refused rather than silently doing a
full run.
"""

from __future__ import annotations

import click

from lexdrift.cli.common import build_engine, engine_options
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_OK, Runtime, cli
from lexdrift.corpus import read_all
from lexdrift.errors import ConfigError
from lexdrift.index.freshness import plan_reindex
from lexdrift.index.store import SqliteIndex
from lexdrift.models import Instrument
from lexdrift.pipeline import Engine

__all__ = ["index"]


def _write(engine: Engine, instrument: Instrument, *, replace: bool) -> int:
    """Index one instrument, recording its identity and digest."""

    if replace:
        engine.index.remove_instrument(instrument.key)
    store = engine.index
    if isinstance(store, SqliteIndex):
        store.register(instrument)
    return engine.add(instrument)


@cli.command(name="index", short_help="build or extend an index from a corpus")
@click.argument("source")
@engine_options()
@click.option("--replace", is_flag=True, help="drop each instrument before adding it again")
@click.option("--incremental", is_flag=True, help="index only what changed since the last run")
@click.option("--stats", is_flag=True, help="print index statistics when the run finishes")
@click.pass_obj
def index(
    runtime: Runtime,
    source: str,
    index_path: str | None,
    config_path: str | None,
    profile: str | None,
    replace: bool,
    incremental: bool,
    stats: bool,
) -> int:
    """Index every document under SOURCE."""

    engine = build_engine(index_path, config_path, profile)
    if incremental and not isinstance(engine.index, SqliteIndex):
        raise ConfigError("incremental indexing needs a durable index", remedy="pass --index")
    instruments = list(read_all(load_corpus(source), engine.registry))
    wanted = {instrument.key.slug for instrument in instruments}
    if incremental:
        assert isinstance(engine.index, SqliteIndex)
        plan = plan_reindex(engine.index, instruments)
        runtime.line(plan.summary())
        wanted = set(plan.to_index)
        for slug in plan.removed:
            runtime.line(f"dropped {slug}")
    added = 0
    documents = 0
    for instrument in instruments:
        if instrument.key.slug not in wanted:
            continue
        written = _write(engine, instrument, replace=replace or incremental)
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

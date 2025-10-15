"""``lexdrift index`` - build or extend an index from a corpus.

Indexing is separate from querying because it is the expensive half and it is
run on a schedule, while a query is run by a person waiting for an answer. The
two commands share the ``--index`` flag and nothing else.
"""

from __future__ import annotations

import argparse
from typing import TextIO

from lexdrift.cli.common import add_engine_options, engine_from
from lexdrift.cli.ingest_cmd import load_corpus
from lexdrift.cli.main import EXIT_OK, Command, register
from lexdrift.corpus import read_all

__all__ = ["configure", "handle"]


def configure(parser: argparse.ArgumentParser) -> None:
    add_engine_options(parser)
    parser.add_argument("source", help="a corpus directory or a manifest.yaml")
    parser.add_argument(
        "--replace",
        action="store_true",
        help="drop each instrument from the index before adding it again",
    )
    parser.add_argument(
        "--stats",
        action="store_true",
        help="print index statistics when the run finishes",
    )


def handle(args: argparse.Namespace, out: TextIO) -> int:
    engine = engine_from(args)
    corpus = load_corpus(args.source)
    added = 0
    documents = 0
    for instrument in read_all(corpus, engine.registry):
        if args.replace:
            engine.index.remove_instrument(instrument.key)
        written = engine.add(instrument)
        added += written
        documents += 1
        print(f"indexed {instrument.key.slug}  {written} segments", file=out)
    print(f"{documents} instruments, {added} segments indexed", file=out)
    if args.stats:
        stats = engine.index.stats()
        print(
            f"index now holds {stats.instruments} instruments, {stats.segments} segments, "
            f"{stats.buckets} buckets, {stats.postings} distinct fingerprints",
            file=out,
        )
        print(f"engine digest {stats.digest}", file=out)
    return EXIT_OK


register(
    Command(
        name="index",
        help="build or extend an index from a corpus",
        configure=configure,
        handler=handle,
    )
)

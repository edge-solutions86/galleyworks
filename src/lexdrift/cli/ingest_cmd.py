"""``lexdrift ingest`` - parse a corpus and report what came back.

It writes nothing and indexes nothing. Its job is to answer "does this directory
parse", which is the first question about any new corpus and the one that is
most annoying to answer by running the whole pipeline.
"""

from __future__ import annotations

import argparse
from typing import TextIO

from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Command, register
from lexdrift.corpus import Corpus, discover, load_manifest, read_all
from lexdrift.errors import IngestError
from lexdrift.ingest.registry import default_registry
from lexdrift.models import Instrument

__all__ = ["configure", "handle", "load_corpus"]


def configure(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("source", help="a corpus directory or a manifest.yaml")
    parser.add_argument(
        "--format",
        dest="format_name",
        default=None,
        help="pin one reader instead of detecting per document",
    )
    parser.add_argument(
        "--quiet",
        action="store_true",
        help="print only the totals",
    )


def load_corpus(source: str) -> Corpus:
    """Load ``source`` as a manifest when it looks like one, else as a directory."""

    if source.endswith((".yaml", ".yml")):
        return load_manifest(source)
    return discover(source)


def handle(args: argparse.Namespace, out: TextIO) -> int:
    corpus = load_corpus(args.source)
    registry = default_registry()
    parsed = 0
    failed = 0
    for entry in corpus:
        try:
            instrument = registry.read(
                entry.source(), format_name=args.format_name or entry.format_name
            )
        except IngestError as exc:
            failed += 1
            print(f"failed  {entry.path}: {exc}", file=out)
            continue
        parsed += 1
        if not args.quiet:
            segments = sum(1 for _ in instrument.body_segments())
            print(
                f"parsed  {instrument.key.slug}  {instrument.source_format}  {segments} segments",
                file=out,
            )
    for path in corpus.skipped:
        print(f"skipped {path}: file name does not name an instrument", file=out)
    print(
        f"{parsed} parsed, {failed} failed, {len(corpus.skipped)} skipped",
        file=out,
    )
    return EXIT_FINDINGS if failed else EXIT_OK


register(
    Command(
        name="ingest",
        help="parse a corpus and report what came back",
        configure=configure,
        handler=handle,
    )
)


def instruments_of(corpus: Corpus) -> list[Instrument]:
    """Parse a corpus, letting errors propagate; used by the other commands."""

    return list(read_all(corpus))

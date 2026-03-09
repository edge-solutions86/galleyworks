"""``lexdrift manifest`` - turn a directory of documents into a manifest.

Discovery from file names is convenient and lossy: it cannot record a chamber,
a pinned reader or a title, and a file that does not follow the convention is
skipped silently on every run. Writing a manifest once turns those decisions
into something that is reviewed and version controlled.

The generated manifest includes the skipped files as commented entries, so the
gap is visible in the file rather than only in the run that produced it.
"""

from __future__ import annotations

from pathlib import Path

import click
import yaml

from lexdrift.cli.main import EXIT_FINDINGS, EXIT_OK, Runtime, cli
from lexdrift.corpus import discover
from lexdrift.errors import ConfigError, IngestError
from lexdrift.ingest.registry import default_registry

__all__ = ["build_manifest", "manifest"]


def build_manifest(root: str, *, enrich: bool = True) -> tuple[dict[str, object], list[Path]]:
    """Build the manifest mapping for ``root`` and the files it could not name."""

    corpus = discover(root)
    registry = default_registry()
    documents: list[dict[str, object]] = []
    for entry in corpus:
        record: dict[str, object] = {
            "path": entry.path.relative_to(corpus.root).as_posix(),
            **{key: str(value) for key, value in sorted(entry.hints.items())},
        }
        if enrich:
            try:
                instrument = registry.read(entry.source())
            except IngestError:
                record["format"] = "plaintext"
            else:
                record["chamber"] = instrument.key.chamber.value
                record["format"] = instrument.source_format
                if instrument.effective_date is not None:
                    record["enacted_on"] = instrument.effective_date.isoformat()
        documents.append(record)
    return {"root": ".", "documents": documents}, list(corpus.skipped)


@cli.command(name="manifest", short_help="write a corpus manifest for a directory")
@click.argument("root")
@click.option(
    "--out",
    "out_path",
    type=click.Path(dir_okay=False),
    default=None,
    help="write the manifest here instead of to standard output",
)
@click.option("--no-enrich", is_flag=True, help="do not parse documents to fill in details")
@click.option("--force", is_flag=True, help="overwrite an existing manifest")
@click.pass_obj
def manifest(
    runtime: Runtime, root: str, out_path: str | None, no_enrich: bool, force: bool
) -> int:
    """Write a manifest describing the corpus under ROOT."""

    payload, skipped = build_manifest(root, enrich=not no_enrich)
    rendered = yaml.safe_dump(payload, sort_keys=False, allow_unicode=True)
    if out_path is None:
        runtime.line(rendered.rstrip())
    else:
        target = Path(out_path)
        if target.exists() and not force:
            raise ConfigError("manifest already exists", path=str(target), remedy="pass --force")
        target.write_text(rendered, encoding="utf-8", newline="\n")
        documents = payload["documents"]
        assert isinstance(documents, list)
        runtime.line(f"{len(documents)} documents written to {target}")
    for path in skipped:
        runtime.line(f"skipped {path}: file name does not name an instrument")
    return EXIT_FINDINGS if skipped else EXIT_OK

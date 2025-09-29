"""Loading a directory of documents as a corpus.

Two ways in. A YAML manifest names every document and its hints explicitly, and
that is what a maintained corpus uses. Discovery reads the hints out of file
names shaped ``<jurisdiction>_<session>_<number>.txt``, which is what a
scratch directory of scraped bills looks like before anyone has curated it.

Discovery never guesses a jurisdiction from a parent directory or from the text.
A file that does not follow the convention is reported as skipped rather than
loaded under a wrong key, because a wrongly attributed instrument corrupts every
later finding about where language originated.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from lexdrift.errors import ConfigError, IngestError
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.registry import ReaderRegistry, default_registry
from lexdrift.models import Instrument
from lexdrift.util.logging import get_logger

__all__ = ["Corpus", "CorpusEntry", "discover", "load_manifest", "read_all"]

_log = get_logger("corpus")
_TEXT_SUFFIXES = frozenset({".txt", ".text", ".bill"})


@dataclass(frozen=True, slots=True)
class CorpusEntry:
    """One document and what the corpus knows about it."""

    path: Path
    hints: dict[str, Any] = field(default_factory=dict)
    format_name: str | None = None

    def source(self) -> SourceDocument:
        return SourceDocument.from_path(self.path, **self.hints)


@dataclass(frozen=True, slots=True)
class Corpus:
    """A set of documents to ingest together."""

    root: Path
    entries: tuple[CorpusEntry, ...] = ()
    skipped: tuple[Path, ...] = ()

    def __len__(self) -> int:
        return len(self.entries)

    def __iter__(self) -> Iterator[CorpusEntry]:
        return iter(self.entries)

    @property
    def jurisdictions(self) -> tuple[str, ...]:
        return tuple(
            sorted({str(entry.hints.get("jurisdiction", "")) for entry in self.entries} - {""})
        )


def _parse_name(path: Path) -> dict[str, Any] | None:
    parts = path.stem.split("_")
    if len(parts) < 3:
        return None
    jurisdiction, session, number = parts[0], parts[1], "_".join(parts[2:])
    if not jurisdiction or not session or not number:
        return None
    return {"jurisdiction": jurisdiction, "session": session, "number": number}


def discover(root: str | Path, *, suffixes: frozenset[str] = _TEXT_SUFFIXES) -> Corpus:
    """Build a corpus from file names under ``root``."""

    base = Path(root)
    if not base.is_dir():
        raise IngestError("corpus root is not a directory", root=str(base))
    entries: list[CorpusEntry] = []
    skipped: list[Path] = []
    for path in sorted(base.rglob("*")):
        if not path.is_file() or path.suffix.lower() not in suffixes:
            continue
        hints = _parse_name(path)
        if hints is None:
            skipped.append(path)
            continue
        entries.append(CorpusEntry(path=path, hints=hints))
    _log.debug(
        "corpus discovered",
        extra={"root": str(base), "found": len(entries), "skipped": len(skipped)},
    )
    return Corpus(root=base, entries=tuple(entries), skipped=tuple(skipped))


def load_manifest(path: str | Path) -> Corpus:
    """Build a corpus from a YAML manifest."""

    manifest = Path(path)
    try:
        raw = yaml.safe_load(manifest.read_text(encoding="utf-8"))
    except OSError as exc:
        raise ConfigError("corpus manifest cannot be read", path=str(manifest)) from exc
    except yaml.YAMLError as exc:
        raise ConfigError("corpus manifest is not valid YAML", path=str(manifest)) from exc
    if not isinstance(raw, dict) or "documents" not in raw:
        raise ConfigError("corpus manifest needs a documents list", path=str(manifest))
    documents = raw["documents"]
    if not isinstance(documents, list):
        raise ConfigError("corpus manifest documents must be a list", path=str(manifest))
    root = manifest.parent / str(raw.get("root", "."))
    entries: list[CorpusEntry] = []
    for position, item in enumerate(documents):
        if not isinstance(item, dict) or "path" not in item:
            raise ConfigError("corpus entry needs a path", path=str(manifest), entry=position)
        hints = {key: value for key, value in item.items() if key not in ("path", "format")}
        entries.append(
            CorpusEntry(
                path=root / str(item["path"]),
                hints=hints,
                format_name=None if item.get("format") is None else str(item["format"]),
            )
        )
    return Corpus(root=root, entries=tuple(entries))


def read_all(corpus: Corpus, registry: ReaderRegistry | None = None) -> Iterator[Instrument]:
    """Parse every entry, in manifest order."""

    readers = registry or default_registry()
    for entry in corpus:
        yield readers.read(entry.source(), format_name=entry.format_name)

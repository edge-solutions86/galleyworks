"""The reader contract every ingest format implements.

A reader is handed a :class:`SourceDocument` and answers two questions: how
confident it is that the bytes are its format, and what instrument they describe.
Sniffing is separate from reading so the registry can pick a winner without
parsing anything.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Protocol, runtime_checkable

from lexdrift.errors import IngestError
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.models import Instrument

__all__ = [
    "Reader",
    "SourceDocument",
    "key_from_hints",
]


@dataclass(frozen=True, slots=True)
class SourceDocument:
    """Raw text plus whatever the caller already knows about it."""

    text: str
    origin: str = "<memory>"
    hints: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_path(cls, path: str | Path, **hints: Any) -> SourceDocument:
        resolved = Path(path)
        try:
            text = resolved.read_text(encoding="utf-8")
        except OSError as exc:
            raise IngestError("source document cannot be read", path=str(resolved)) from exc
        return cls(text=text, origin=str(resolved), hints=dict(hints))

    @property
    def first_lines(self) -> list[str]:
        """The opening lines a sniffer should look at, cheaply."""

        return self.text.split("\n", 40)[:40]

    def hint(self, name: str, default: Any = None) -> Any:
        return self.hints.get(name, default)


@runtime_checkable
class Reader(Protocol):
    """A format-specific document reader."""

    name: str

    def sniff(self, document: SourceDocument) -> float:
        """Confidence in ``[0, 1]`` that this reader understands the document."""

    def read(self, document: SourceDocument) -> Instrument:
        """Parse the document into an instrument, or raise ``IngestError``."""


def key_from_hints(document: SourceDocument) -> InstrumentKey:
    """Build an instrument key from caller hints, failing loudly when short."""

    missing = [name for name in ("jurisdiction", "session", "number") if not document.hint(name)]
    if missing:
        raise IngestError(
            "source document is missing identity hints",
            origin=document.origin,
            missing=",".join(missing),
        )
    raw_chamber = document.hint("chamber", Chamber.LOWER)
    try:
        chamber = Chamber(raw_chamber)
    except ValueError as exc:
        raise IngestError(
            "source document names an unknown chamber",
            origin=document.origin,
            chamber=str(raw_chamber),
        ) from exc
    return InstrumentKey(
        jurisdiction=str(document.hint("jurisdiction")),
        session=str(document.hint("session")),
        chamber=chamber,
        number=str(document.hint("number")),
    )

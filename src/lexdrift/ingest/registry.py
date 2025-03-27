"""Reader registry and format detection.

Detection is a plain argmax over sniff scores with deterministic tie-breaking:
registration order decides, so adding a reader never silently changes how an
existing corpus is parsed unless it actually scores higher.
"""

from __future__ import annotations

from dataclasses import dataclass

from lexdrift.errors import IngestError, UnknownFormatError
from lexdrift.ingest.base import Reader, SourceDocument
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.ingest.plaintext import PlaintextReader
from lexdrift.models import Instrument
from lexdrift.util.logging import get_logger

__all__ = [
    "MIN_CONFIDENCE",
    "Detection",
    "ReaderRegistry",
    "default_registry",
]

MIN_CONFIDENCE = 0.01

_log = get_logger("ingest.registry")


@dataclass(frozen=True, slots=True)
class Detection:
    """Which reader won, and by how much."""

    reader: Reader
    score: float
    runner_up: str | None = None

    @property
    def name(self) -> str:
        return self.reader.name


class ReaderRegistry:
    """An ordered collection of readers that can pick one for a document."""

    def __init__(self) -> None:
        self._readers: list[Reader] = []

    def register(self, reader: Reader) -> None:
        if any(existing.name == reader.name for existing in self._readers):
            raise IngestError("a reader with this name is already registered", name=reader.name)
        self._readers.append(reader)

    @property
    def names(self) -> list[str]:
        return [reader.name for reader in self._readers]

    def get(self, name: str) -> Reader:
        for reader in self._readers:
            if reader.name == name:
                return reader
        raise UnknownFormatError("no reader with this name", name=name, known=",".join(self.names))

    def detect(self, document: SourceDocument) -> Detection:
        """Return the highest scoring reader for ``document``."""

        if not self._readers:
            raise UnknownFormatError("no readers registered", origin=document.origin)
        scored = [
            (reader.sniff(document), position, reader)
            for position, reader in enumerate(self._readers)
        ]
        scored.sort(key=lambda item: (-item[0], item[1]))
        best_score, _, best = scored[0]
        if best_score < MIN_CONFIDENCE:
            raise UnknownFormatError(
                "no reader recognised the document",
                origin=document.origin,
                best=best.name,
                score=round(best_score, 4),
            )
        runner_up = scored[1][2].name if len(scored) > 1 else None
        return Detection(reader=best, score=best_score, runner_up=runner_up)

    def read(self, document: SourceDocument, *, format_name: str | None = None) -> Instrument:
        """Parse ``document``, either with a named reader or the detected one."""

        if format_name is not None:
            reader = self.get(format_name)
            _log.debug("reading with a pinned reader", extra={"reader": reader.name})
            return reader.read(document)
        detection = self.detect(document)
        _log.debug(
            "reader detected",
            extra={"reader": detection.name, "score": detection.score, "origin": document.origin},
        )
        return detection.reader.read(document)


def default_registry() -> ReaderRegistry:
    """The registry the CLI and the service use."""

    registry = ReaderRegistry()
    registry.register(EnrolledReader())
    registry.register(PlaintextReader())
    return registry

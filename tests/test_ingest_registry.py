from __future__ import annotations

import pytest

from lexdrift.errors import IngestError, UnknownFormatError
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.plaintext import PlaintextReader
from lexdrift.ingest.registry import ReaderRegistry, default_registry
from lexdrift.models import Instrument

HINTS = {"jurisdiction": "wy", "session": "2025", "number": "HB0142"}


def doc(text: str = "AN ACT relating to water.\n") -> SourceDocument:
    return SourceDocument(text=text, origin="t.txt", hints=dict(HINTS))


class StubReader:
    def __init__(self, name: str, score: float) -> None:
        self.name = name
        self._score = score
        self.reads = 0

    def sniff(self, document: SourceDocument) -> float:
        return self._score

    def read(self, document: SourceDocument) -> Instrument:
        self.reads += 1
        return PlaintextReader().read(document)


def test_default_registry_has_the_fallback() -> None:
    assert default_registry().names == ["plaintext"]


def test_duplicate_names_are_rejected() -> None:
    registry = ReaderRegistry()
    registry.register(StubReader("enrolled", 0.5))
    with pytest.raises(IngestError, match="already registered"):
        registry.register(StubReader("enrolled", 0.9))


def test_get_finds_a_reader_by_name() -> None:
    registry = default_registry()
    assert registry.get("plaintext").name == "plaintext"


def test_get_reports_the_known_names() -> None:
    registry = default_registry()
    with pytest.raises(UnknownFormatError, match="no reader with this name") as caught:
        registry.get("enrolled")
    assert caught.value.context["known"] == "plaintext"


def test_detect_picks_the_highest_score() -> None:
    registry = ReaderRegistry()
    registry.register(StubReader("low", 0.2))
    registry.register(StubReader("high", 0.8))
    detection = registry.detect(doc())
    assert detection.name == "high"
    assert detection.score == pytest.approx(0.8)
    assert detection.runner_up == "low"


def test_ties_go_to_the_earlier_registration() -> None:
    registry = ReaderRegistry()
    registry.register(StubReader("first", 0.5))
    registry.register(StubReader("second", 0.5))
    assert registry.detect(doc()).name == "first"


def test_detect_on_an_empty_registry_is_an_error() -> None:
    with pytest.raises(UnknownFormatError, match="no readers registered"):
        ReaderRegistry().detect(doc())


def test_detect_refuses_scores_below_the_floor() -> None:
    registry = ReaderRegistry()
    registry.register(StubReader("weak", 0.0))
    with pytest.raises(UnknownFormatError, match="no reader recognised") as caught:
        registry.detect(doc())
    assert caught.value.context["best"] == "weak"


def test_single_reader_has_no_runner_up() -> None:
    assert default_registry().detect(doc()).runner_up is None


def test_read_uses_the_detected_reader() -> None:
    registry = ReaderRegistry()
    winner = StubReader("high", 0.9)
    registry.register(StubReader("low", 0.1))
    registry.register(winner)
    registry.read(doc())
    assert winner.reads == 1


def test_read_can_pin_a_reader() -> None:
    registry = ReaderRegistry()
    loser = StubReader("low", 0.1)
    registry.register(loser)
    registry.register(StubReader("high", 0.9))
    registry.read(doc(), format_name="low")
    assert loser.reads == 1


def test_pinning_an_unknown_reader_is_an_error() -> None:
    with pytest.raises(UnknownFormatError):
        default_registry().read(doc(), format_name="enrolled")


def test_read_returns_a_parsed_instrument() -> None:
    instrument = default_registry().read(doc())
    assert instrument.key.slug == "wy/2025/lower/HB0142"
    assert len(instrument.segments) == 1

from __future__ import annotations

from pathlib import Path

import pytest

from lexdrift.errors import IngestError
from lexdrift.identity import Chamber
from lexdrift.ingest.base import Reader, SourceDocument, key_from_hints
from lexdrift.ingest.plaintext import PlaintextReader

HINTS = {"jurisdiction": "wy", "session": "2025", "number": "HB0142"}

BODY = "AN ACT relating to water.\n\nThe board shall publish a schedule.\n\n\nEffective July 1.\n"


def doc(text: str = BODY, **extra: object) -> SourceDocument:
    return SourceDocument(text=text, origin="t.txt", hints={**HINTS, **extra})


def test_reader_protocol_is_satisfied() -> None:
    assert isinstance(PlaintextReader(), Reader)


def test_from_path_reads_utf8(tmp_path: Path) -> None:
    path = tmp_path / "bill.txt"
    path.write_text("señor\n", encoding="utf-8")
    source = SourceDocument.from_path(path, jurisdiction="wy")
    assert source.text == "señor\n"
    assert source.hint("jurisdiction") == "wy"


def test_from_path_reports_a_missing_file(tmp_path: Path) -> None:
    with pytest.raises(IngestError, match="cannot be read"):
        SourceDocument.from_path(tmp_path / "absent.txt")


def test_first_lines_is_capped() -> None:
    source = SourceDocument(text="\n".join(str(i) for i in range(200)))
    assert len(source.first_lines) == 40


def test_key_from_hints_builds_the_key() -> None:
    key = key_from_hints(doc(chamber="upper"))
    assert key.chamber is Chamber.UPPER
    assert key.slug == "wy/2025/upper/HB0142"


def test_key_from_hints_defaults_the_chamber() -> None:
    assert key_from_hints(doc()).chamber is Chamber.LOWER


def test_key_from_hints_names_every_missing_field() -> None:
    source = SourceDocument(text="x", hints={"jurisdiction": "wy"})
    with pytest.raises(IngestError, match="missing identity hints") as caught:
        key_from_hints(source)
    assert caught.value.context["missing"] == "session,number"


def test_key_from_hints_rejects_an_unknown_chamber() -> None:
    with pytest.raises(IngestError, match="unknown chamber"):
        key_from_hints(doc(chamber="senate"))


def test_blocks_become_sections() -> None:
    instrument = PlaintextReader().read(doc())
    assert len(instrument.segments) == 3
    assert instrument.source_format == "plaintext"
    texts = instrument.text_for(instrument.segments)
    assert texts[0] == "AN ACT relating to water."
    assert texts[2] == "Effective July 1."


def test_paths_are_numbered_from_one() -> None:
    instrument = PlaintextReader().read(doc())
    assert [s.path.printed for s in instrument.segments] == ["1", "2", "3"]


def test_blank_document_yields_no_segments() -> None:
    instrument = PlaintextReader().read(doc(text="   \n\n  \n"))
    assert instrument.segments == []
    assert instrument.title == ""


def test_title_hint_wins_over_the_first_line() -> None:
    instrument = PlaintextReader().read(doc(title="Water rights"))
    assert instrument.title == "Water rights"


def test_sniff_is_low_but_non_zero_for_text() -> None:
    reader = PlaintextReader()
    assert reader.sniff(doc()) == pytest.approx(0.05)
    assert reader.sniff(doc(text="  \n ")) == 0.0


def test_whitespace_only_blocks_are_dropped() -> None:
    instrument = PlaintextReader().read(doc(text="a\n\n   \n\nb"))
    assert instrument.text_for(instrument.segments) == ["a", "b"]

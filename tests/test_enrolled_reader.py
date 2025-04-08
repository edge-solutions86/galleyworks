from __future__ import annotations

import pytest

from lexdrift.errors import ParseError
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.blocks import split_sections
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.ingest.enrolled_lex import lex_lines
from lexdrift.ingest.plaintext import PlaintextReader
from lexdrift.models import SegmentKind


def test_sniff_beats_the_plaintext_fallback(water_bill: SourceDocument) -> None:
    assert EnrolledReader().sniff(water_bill) > PlaintextReader().sniff(water_bill)


def test_sniff_scores_each_marker(water_bill: SourceDocument) -> None:
    assert EnrolledReader().sniff(water_bill) == pytest.approx(1.0)
    partial = SourceDocument(text="Section 1.  Something.\n", hints=water_bill.hints)
    assert EnrolledReader().sniff(partial) == pytest.approx(0.2)


def test_sniff_is_zero_without_numbered_sections(water_bill: SourceDocument) -> None:
    plain = SourceDocument(text="a memo about nothing\n", hints=water_bill.hints)
    assert EnrolledReader().sniff(plain) == 0.0
    titled = SourceDocument(text="AN ACT relating to water.\n", hints=water_bill.hints)
    assert EnrolledReader().sniff(titled) == 0.0


def test_sections_are_split_at_the_openers(water_bill_text: str) -> None:
    blocks = split_sections(water_bill_text, lex_lines(water_bill_text))
    assert [block.label for block in blocks] == ["1", "2"]


def test_section_span_stops_before_the_next_section(water_bill_text: str) -> None:
    blocks = split_sections(water_bill_text, lex_lines(water_bill_text))
    first = blocks[0].span.slice_of(water_bill_text)
    assert first.startswith("Section 1.")
    assert "Section 2." not in first
    assert first.endswith("temporary appropriation.")


def test_section_heading_comes_from_the_catchline(water_bill_text: str) -> None:
    blocks = split_sections(water_bill_text, lex_lines(water_bill_text))
    assert blocks[0].heading == "Schedule of appropriations."
    assert blocks[1].heading == ""


def test_split_sections_on_text_with_none() -> None:
    assert split_sections("nothing here\n", lex_lines("nothing here\n")) == []


def test_read_builds_the_whole_tree(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    kinds = [segment.kind for segment in instrument.segments]
    assert kinds == [
        SegmentKind.TITLE,
        SegmentKind.ENACTING,
        SegmentKind.SECTION,
        SegmentKind.SUBSECTION,
        SegmentKind.PARAGRAPH,
        SegmentKind.PARAGRAPH,
        SegmentKind.SUBSECTION,
        SegmentKind.SECTION,
    ]
    assert instrument.source_format == "enrolled"


def test_read_indexes_segments_consecutively(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    assert [s.index for s in instrument.segments] == list(range(len(instrument.segments)))


def test_read_links_children_to_their_section(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    sections = [s for s in instrument.segments if s.kind is SegmentKind.SECTION]
    first = sections[0]
    assert [c.path.printed for c in instrument.children_of(first.index)] == ["1(a)", "1(b)"]


def test_read_gives_every_segment_a_path(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    paths = [s.path.printed for s in instrument.segments if s.path.depth]
    assert paths == ["1", "1(a)", "1(a)(i)", "1(a)(ii)", "1(b)", "2"]


def test_body_segments_exclude_the_enacting_clause(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    assert all(segment.kind is not SegmentKind.ENACTING for segment in instrument.body_segments())


def test_long_title_is_captured(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    assert instrument.title.startswith("AN ACT relating to water rights")


def test_title_hint_overrides_the_long_title(water_bill: SourceDocument) -> None:
    source = SourceDocument(
        text=water_bill.text, origin="x", hints={**water_bill.hints, "title": "Short"}
    )
    assert EnrolledReader().read(source).title == "Short"


def test_section_paths_use_the_printed_number(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    sections = [s for s in instrument.segments if s.kind is SegmentKind.SECTION]
    assert [s.path.printed for s in sections] == ["1", "2"]


def test_missing_sections_are_a_parse_error(water_bill: SourceDocument) -> None:
    source = SourceDocument(text="AN ACT relating to nothing.\n", hints=water_bill.hints)
    with pytest.raises(ParseError, match="no numbered sections"):
        EnrolledReader().read(source)


def test_segment_spans_stay_inside_the_document(water_bill: SourceDocument) -> None:
    instrument = EnrolledReader().read(water_bill)
    for segment in instrument.segments:
        assert segment.span.end <= len(instrument.raw_text)
        assert segment.span.slice_of(instrument.raw_text).strip() != ""

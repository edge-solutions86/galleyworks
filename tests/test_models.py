from __future__ import annotations

from datetime import date

import pytest

from lexdrift.errors import IngestError
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.models import Instrument, Segment, SegmentKind, SegmentPath
from lexdrift.util.spans import TextSpan

TEXT = "AN ACT relating to water rights. Section 1. The board shall publish a schedule."


def key() -> InstrumentKey:
    return InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142")


def test_segment_kind_depth_is_ordered() -> None:
    assert SegmentKind.SECTION.depth < SegmentKind.SUBSECTION.depth
    assert SegmentKind.SUBSECTION.depth < SegmentKind.PARAGRAPH.depth


def test_only_enacted_substance_is_body() -> None:
    assert not SegmentKind.ENACTING.is_body
    assert not SegmentKind.PREAMBLE.is_body
    assert not SegmentKind.TITLE.is_body
    assert SegmentKind.SECTION.is_body
    assert SegmentKind.SUBSECTION.is_body


def test_segment_path_builds_and_prints() -> None:
    path = SegmentPath().child("3").child("a").child("ii")
    assert path.depth == 3
    assert path.dotted == "3.a.ii"
    assert path.printed == "3(a)(ii)"
    assert str(path) == "3(a)(ii)"


def test_empty_segment_path_prints_empty() -> None:
    assert SegmentPath().printed == ""


def test_segment_path_rejects_blank_labels() -> None:
    with pytest.raises(IngestError, match="label is empty"):
        SegmentPath().child("  ")


def test_segment_path_ancestry() -> None:
    parent = SegmentPath(("3",))
    child = SegmentPath(("3", "a"))
    assert parent.is_ancestor_of(child)
    assert not child.is_ancestor_of(parent)
    assert not parent.is_ancestor_of(parent)


def test_segment_rejects_forward_parent() -> None:
    with pytest.raises(IngestError, match="must precede the child"):
        Segment(
            index=1,
            kind=SegmentKind.SECTION,
            path=SegmentPath(("1",)),
            span=TextSpan(0, 5),
            parent=4,
        )


def test_segment_rejects_negative_index() -> None:
    with pytest.raises(IngestError, match="non-negative"):
        Segment(index=-1, kind=SegmentKind.SECTION, path=SegmentPath(), span=TextSpan(0, 1))


def test_instrument_rejects_spans_past_the_text() -> None:
    bad = Segment(
        index=0, kind=SegmentKind.SECTION, path=SegmentPath(("1",)), span=TextSpan(0, 500)
    )
    with pytest.raises(IngestError, match="past the end"):
        Instrument(key=key(), title="Water rights", raw_text=TEXT, segments=[bad])


def test_instrument_navigation() -> None:
    enacting = Segment(index=0, kind=SegmentKind.ENACTING, path=SegmentPath(), span=TextSpan(0, 32))
    section = Segment(
        index=1, kind=SegmentKind.SECTION, path=SegmentPath(("1",)), span=TextSpan(33, len(TEXT))
    )
    child = Segment(
        index=2,
        kind=SegmentKind.SUBSECTION,
        path=SegmentPath(("1", "a")),
        span=TextSpan(45, len(TEXT)),
        parent=1,
    )
    doc = Instrument(
        key=key(),
        title="Water rights",
        raw_text=TEXT,
        segments=[enacting, section, child],
        enacted_on=date(2025, 3, 4),
    )
    assert [s.index for s in doc.body_segments()] == [1, 2]
    assert doc.segment_at(2) is child
    assert doc.children_of(1) == [child]
    assert doc.effective_date == date(2025, 3, 4)
    assert doc.text_for([section])[0].startswith("Section 1.")


def test_effective_date_falls_back_to_introduction() -> None:
    doc = Instrument(key=key(), title="t", raw_text=TEXT, introduced_on=date(2025, 1, 9))
    assert doc.effective_date == date(2025, 1, 9)
    assert Instrument(key=key(), title="t", raw_text=TEXT).effective_date is None


def test_segment_at_reports_a_missing_index() -> None:
    doc = Instrument(key=key(), title="t", raw_text=TEXT)
    with pytest.raises(IngestError, match="no such segment"):
        doc.segment_at(7)


def test_segment_text_reads_from_the_owning_instrument() -> None:
    section = Segment(
        index=0, kind=SegmentKind.SECTION, path=SegmentPath(("1",)), span=TextSpan(33, 43)
    )
    doc = Instrument(key=key(), title="t", raw_text=TEXT, segments=[section])
    assert section.text_of(doc) == "Section 1."

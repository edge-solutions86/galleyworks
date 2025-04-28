from __future__ import annotations

import pytest

from lexdrift.ingest.amendment import AmendmentReader, Reading
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.registry import default_registry
from lexdrift.models import SegmentKind

DRAFT = """AN ACT relating to water rights.

Be It Enacted by the Legislature of the State of Wyoming:

Section 1.  W.S. 41-3-101 is amended to read:

41-3-101.  Schedule of appropriations.

(a)  The board shall publish a schedule not later than {-sixty (60)-}{+ninety (90)+}
days after the close of each irrigation season.

(b)  This section does not apply to a temporary appropriation.
"""

HINTS = {"jurisdiction": "wy", "session": "2025", "number": "HB0142"}


def draft(text: str = DRAFT) -> SourceDocument:
    return SourceDocument(text=text, origin="hb0142-amd.txt", hints=dict(HINTS))


def test_the_reader_names_its_reading() -> None:
    assert AmendmentReader().name == "amendment-proposed"
    assert AmendmentReader(Reading.CURRENT).name == "amendment-current"


def test_sniff_needs_both_markup_and_structure() -> None:
    assert AmendmentReader().sniff(draft()) == pytest.approx(1.0)
    assert AmendmentReader().sniff(draft("no markup here\n")) == 0.0
    assert AmendmentReader().sniff(draft("{-struck-} but no sections\n")) == 0.0


def test_amendment_outsniffs_the_plain_enrolled_reader() -> None:
    detection = default_registry().detect(draft())
    assert detection.name == "amendment-proposed"


def test_the_proposed_reading_drops_struck_words() -> None:
    instrument = AmendmentReader().read(draft())
    section = next(s for s in instrument.segments if s.kind is SegmentKind.SECTION)
    body = section.span.slice_of(instrument.raw_text)
    assert "{+ninety (90)+}" in body
    assert body.count("sixty") == 1


def test_the_raw_text_is_always_the_markup() -> None:
    instrument = AmendmentReader().read(draft())
    assert instrument.raw_text == DRAFT
    assert instrument.source_format == "amendment-proposed"


def test_both_readings_find_the_same_structure() -> None:
    proposed = AmendmentReader().read(draft())
    current = AmendmentReader(Reading.CURRENT).read(draft())
    assert [s.path.printed for s in proposed.segments] == [s.path.printed for s in current.segments]
    assert [s.kind for s in proposed.segments] == [s.kind for s in current.segments]


def test_spans_land_inside_the_markup() -> None:
    for reading in (Reading.CURRENT, Reading.PROPOSED):
        instrument = AmendmentReader(reading).read(draft())
        for segment in instrument.segments:
            assert segment.span.end <= len(instrument.raw_text)
            assert segment.span.slice_of(instrument.raw_text).strip() != ""


def test_a_subsection_span_still_covers_its_own_text() -> None:
    instrument = AmendmentReader().read(draft())
    subsections = [s for s in instrument.segments if s.kind is SegmentKind.SUBSECTION]
    first = subsections[0].span.slice_of(instrument.raw_text)
    assert first.startswith("(a)")
    assert "temporary appropriation" not in first


def test_identity_and_title_survive_the_round_trip() -> None:
    instrument = AmendmentReader().read(draft())
    assert instrument.key.slug == "wy/2025/lower/HB0142"
    assert instrument.title.startswith("AN ACT relating to water rights")


def test_a_document_without_markup_is_left_to_the_enrolled_reader() -> None:
    plain = DRAFT.replace("{-sixty (60)-}{+ninety (90)+}", "sixty (60)")
    assert default_registry().detect(draft(plain)).name == "enrolled"

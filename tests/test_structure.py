from __future__ import annotations

from lexdrift.ingest.enrolled import split_sections
from lexdrift.ingest.enrolled_lex import lex_lines
from lexdrift.ingest.structure import nest_section
from lexdrift.models import Segment, SegmentKind, SegmentPath

NESTED = """Section 1.  W.S. 1-1-101 is amended to read:

(a)  The board shall act.

    (i)  by publishing a notice; and

    (ii)  by mailing the notice.

        (A)  first class postage; or

            (1)  metered mail is allowed.

(b)  This section is directory.
"""

LONG_LIST = """Section 1.  W.S. 1-1-101 is amended to read:

(f)  Sixth.

(g)  Seventh.

(h)  Eighth.

(i)  Ninth, not roman one.

(j)  Tenth.
"""

EARLY_ROMAN = """Section 1.  W.S. 1-1-101 is amended to read:

(a)  The board shall act.

(i)  by publishing a notice; and

(ii)  by mailing the notice.
"""


def build(text: str) -> list[Segment]:
    block = split_sections(text, lex_lines(text))[0]
    return nest_section(text, block, first_index=1, parent=0, parent_path=SegmentPath(("1",)))


def test_nesting_assigns_kinds_by_depth() -> None:
    segments = build(NESTED)
    assert [s.kind for s in segments] == [
        SegmentKind.SUBSECTION,
        SegmentKind.PARAGRAPH,
        SegmentKind.PARAGRAPH,
        SegmentKind.SUBPARAGRAPH,
        SegmentKind.ITEM,
        SegmentKind.SUBSECTION,
    ]


def test_paths_stack_under_the_section() -> None:
    segments = build(NESTED)
    assert [s.path.printed for s in segments] == [
        "1(a)",
        "1(a)(i)",
        "1(a)(ii)",
        "1(a)(ii)(A)",
        "1(a)(ii)(A)(1)",
        "1(b)",
    ]


def test_parents_point_at_the_enclosing_segment() -> None:
    segments = build(NESTED)
    by_path = {s.path.printed: s for s in segments}
    assert by_path["1(a)"].parent == 0
    assert by_path["1(a)(i)"].parent == by_path["1(a)"].index
    assert by_path["1(a)(ii)(A)"].parent == by_path["1(a)(ii)"].index
    assert by_path["1(b)"].parent == 0


def test_a_subsection_span_contains_its_paragraphs() -> None:
    segments = build(NESTED)
    subsection = segments[0]
    paragraph = segments[1]
    assert subsection.span.contains(paragraph.span)
    assert "by mailing the notice" in subsection.span.slice_of(NESTED)
    assert "This section is directory" not in subsection.span.slice_of(NESTED)


def test_spans_are_right_trimmed() -> None:
    for segment in build(NESTED):
        sliced = segment.span.slice_of(NESTED)
        assert sliced == sliced.rstrip()


def test_i_after_h_continues_the_alphabetic_list() -> None:
    segments = build(LONG_LIST)
    assert [s.kind for s in segments] == [SegmentKind.SUBSECTION] * 5
    assert [s.path.printed for s in segments] == ["1(f)", "1(g)", "1(h)", "1(i)", "1(j)"]


def test_i_after_a_opens_a_roman_list() -> None:
    segments = build(EARLY_ROMAN)
    assert [s.kind for s in segments] == [
        SegmentKind.SUBSECTION,
        SegmentKind.PARAGRAPH,
        SegmentKind.PARAGRAPH,
    ]
    assert segments[1].parent == segments[0].index


def test_a_section_with_no_labels_has_no_children() -> None:
    text = "Section 2.  This act is effective July 1, 2025.\n"
    assert build(text) == []


def test_v_continues_a_roman_list() -> None:
    text = (
        "Section 1.  W.S. 1-1-101 is amended to read:\n\n"
        "(a)  Lead in.\n\n"
        "    (iii)  third;\n\n"
        "    (iv)  fourth;\n\n"
        "    (v)  fifth.\n"
    )
    segments = build(text)
    assert [s.kind for s in segments[1:]] == [SegmentKind.PARAGRAPH] * 3


def test_indent_decides_when_no_sequence_matches() -> None:
    text = (
        "Section 1.  W.S. 1-1-101 is amended to read:\n\n"
        "(a)  Lead in.\n\n"
        "        (x)  a stray tenth.\n"
    )
    segments = build(text)
    assert segments[1].kind is SegmentKind.PARAGRAPH

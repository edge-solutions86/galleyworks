from __future__ import annotations

import pytest

from lexdrift.ingest.enrolled_lex import LineKind, lex_lines

BILL = """AN ACT relating to water rights.

Be It Enacted by the Legislature of the State of Wyoming:

Section 1.  W.S. 41-3-101 is amended to read:

41-3-101.  Schedule of appropriations.

(a)  The board shall publish a schedule.

    (i)  The schedule shall include the priority date.

    (B)  Fees are not refundable.

    (3)  The board may charge a fee.
"""


def kinds(text: str) -> list[LineKind]:
    return [line.kind for line in lex_lines(text) if line.kind is not LineKind.BLANK]


def test_every_line_is_classified() -> None:
    assert kinds(BILL) == [
        LineKind.TITLE,
        LineKind.ENACTING,
        LineKind.SECTION,
        LineKind.CATCHLINE,
        LineKind.SUBSECTION,
        LineKind.AMBIGUOUS_LABEL,
        LineKind.SUBPARAGRAPH,
        LineKind.ITEM,
    ]


def test_spans_cover_the_source_lines() -> None:
    for line in lex_lines(BILL):
        assert line.span.slice_of(BILL) == BILL.split("\n")[line.number - 1]


def test_line_numbers_start_at_one() -> None:
    assert lex_lines("a\nb")[0].number == 1
    assert lex_lines("a\nb")[1].number == 2


def test_section_label_and_body() -> None:
    line = next(line for line in lex_lines(BILL) if line.kind is LineKind.SECTION)
    assert line.label == "1"
    assert line.body is not None
    assert line.body.slice_of(BILL).startswith("W.S. 41-3-101")


def test_catchline_keeps_the_statute_number() -> None:
    line = next(line for line in lex_lines(BILL) if line.kind is LineKind.CATCHLINE)
    assert line.label == "41-3-101"
    assert line.body is not None
    assert line.body.slice_of(BILL).strip() == "Schedule of appropriations."


def test_indent_is_recorded() -> None:
    paragraph = next(line for line in lex_lines(BILL) if line.kind is LineKind.AMBIGUOUS_LABEL)
    subsection = next(line for line in lex_lines(BILL) if line.kind is LineKind.SUBSECTION)
    assert subsection.indent == 0
    assert paragraph.indent == 4


def test_roman_i_is_not_resolved_by_the_lexer() -> None:
    assert kinds("(i)  text\n") == [LineKind.AMBIGUOUS_LABEL]
    assert kinds("(v)  text\n") == [LineKind.AMBIGUOUS_LABEL]


@pytest.mark.parametrize(
    ("line", "kind"),
    [
        ("(a)  text", LineKind.SUBSECTION),
        ("(b)  text", LineKind.SUBSECTION),
        ("(ii)  text", LineKind.PARAGRAPH),
        ("(iii)  text", LineKind.PARAGRAPH),
        ("(A)  text", LineKind.SUBPARAGRAPH),
        ("(12)  text", LineKind.ITEM),
    ],
)
def test_label_classification(line: str, kind: LineKind) -> None:
    assert kinds(line) == [kind]


def test_unusable_labels_fall_back_to_text() -> None:
    assert kinds("(a1)  text") == [LineKind.TEXT]
    assert kinds("(abcd)  text") == [LineKind.TEXT]


def test_blank_lines_have_zero_indent_and_no_label() -> None:
    blank = lex_lines("   \nx")[0]
    assert blank.kind is LineKind.BLANK
    assert blank.indent == 0
    assert not blank.has_label


def test_section_matching_is_case_insensitive() -> None:
    assert kinds("SECTION 4.  text") == [LineKind.SECTION]


def test_catchline_needs_text_after_the_number() -> None:
    assert kinds("41-3-101.") == [LineKind.TEXT]


def test_has_label_covers_the_structural_kinds() -> None:
    labelled = [line for line in lex_lines(BILL) if line.has_label]
    assert {line.kind for line in labelled} == {
        LineKind.SECTION,
        LineKind.SUBSECTION,
        LineKind.AMBIGUOUS_LABEL,
        LineKind.SUBPARAGRAPH,
        LineKind.ITEM,
    }


def test_empty_text_gives_one_blank_line() -> None:
    assert kinds("") == []
    assert len(lex_lines("")) == 1

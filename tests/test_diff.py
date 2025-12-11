from __future__ import annotations

from conftest_corpus import MODEL_CLAUSE, make_bill
from lexdrift.canon.profiles import profile
from lexdrift.diff import ChangeKind, compare


def bill(body: str, number: str = "HB0142"):
    return make_bill(jurisdiction="wy", number=number, body=body)


def test_a_document_compared_with_itself_is_identical() -> None:
    one = bill(MODEL_CLAUSE)
    result = compare(one, one)
    assert result.is_identical
    assert result.changed == ()
    assert result.total_churn == 0


def test_the_two_sides_are_named() -> None:
    result = compare(bill(MODEL_CLAUSE), bill(MODEL_CLAUSE, number="HB0143"))
    assert result.left == "wy/2025/lower/HB0142"
    assert result.right == "wy/2025/lower/HB0143"


def test_a_reworded_subsection_is_changed() -> None:
    other = MODEL_CLAUSE.replace("sixty\n(60) days", "ninety (90) days")
    result = compare(bill(MODEL_CLAUSE), bill(other))
    changed = {change.path for change in result.changed}
    assert "1(a)" in changed
    assert not result.is_identical


def test_a_changed_segment_reports_its_churn() -> None:
    other = MODEL_CLAUSE.replace("priority date", "diversion point")
    result = compare(bill(MODEL_CLAUSE), bill(other))
    change = next(c for c in result.changes if c.path == "1(a)")
    assert change.kind is ChangeKind.CHANGED
    assert change.words_added > 0
    assert change.words_removed > 0
    assert 0.5 < change.similarity < 1.0


def test_an_added_subsection_is_reported() -> None:
    extended = f"{MODEL_CLAUSE}\n\n(b)  The board may charge a fee for a certified copy."
    result = compare(bill(MODEL_CLAUSE), bill(extended))
    added = [c for c in result.changes if c.kind is ChangeKind.ADDED]
    assert [c.path for c in added] == ["1(b)"]
    assert added[0].words_added > 0
    assert added[0].similarity == 0.0


def test_a_removed_subsection_is_reported() -> None:
    extended = f"{MODEL_CLAUSE}\n\n(b)  The board may charge a fee for a certified copy."
    result = compare(bill(extended), bill(MODEL_CLAUSE))
    removed = [c for c in result.changes if c.kind is ChangeKind.REMOVED]
    assert [c.path for c in removed] == ["1(b)"]
    assert removed[0].words_removed > 0


def test_renumbering_shows_as_a_removal_and_an_addition() -> None:
    left = f"{MODEL_CLAUSE}\n\n(b)  The board may charge a fee."
    right = f"{MODEL_CLAUSE}\n\n(c)  The board may charge a fee."
    kinds = {change.path: change.kind for change in compare(bill(left), bill(right)).changes}
    assert kinds["1(b)"] is ChangeKind.REMOVED
    assert kinds["1(c)"] is ChangeKind.ADDED


def test_counts_add_up() -> None:
    other = MODEL_CLAUSE.replace("priority date", "diversion point")
    result = compare(bill(MODEL_CLAUSE), bill(other))
    counts = result.counts()
    assert sum(counts.values()) == len(result.changes)
    assert counts["changed"] >= 1


def test_changes_come_back_in_path_order() -> None:
    extended = f"{MODEL_CLAUSE}\n\n(b)  A fee applies."
    result = compare(bill(MODEL_CLAUSE), bill(extended))
    paths = [change.path for change in result.changes]
    assert paths == sorted(paths)


def test_a_verbatim_profile_notices_a_case_change() -> None:
    other = MODEL_CLAUSE.replace("The board", "THE BOARD")
    assert not compare(bill(MODEL_CLAUSE), bill(other)).is_identical


def test_the_default_profile_can_be_overridden_to_ignore_case() -> None:
    other = MODEL_CLAUSE.replace("The board", "THE BOARD")
    result = compare(bill(MODEL_CLAUSE), bill(other), profile("default"))
    assert result.is_identical


def test_churn_totals_across_segments() -> None:
    other = MODEL_CLAUSE.replace("priority date", "diversion point")
    result = compare(bill(MODEL_CLAUSE), bill(other))
    assert result.total_churn == sum(change.churn for change in result.changes)
    assert result.total_churn > 0

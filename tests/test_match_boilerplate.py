from __future__ import annotations

from conftest_corpus import LOCAL_CLAUSE, MODEL_CLAUSE, fingerprints, make_bill
from lexdrift.index.memory import MemoryIndex
from lexdrift.match.boilerplate import BoilerplateFilter, learn_boilerplate
from lexdrift.match.engine import Matcher

RECITAL = (
    "Notwithstanding any other provision of law, and in accordance with the\n"
    "policy of this state, the following provisions apply to every district\n"
    "organized under this chapter without regard to the date of organization."
)


SUBJECTS = (
    "well permits",
    "grazing leases",
    "ditch rights of way",
    "reservoir storage",
    "stock watering ponds",
    "drainage districts",
    "county fair premiums",
    "veterinary licences",
    "brand inspection fees",
    "predator control levies",
    "seed certification",
)


def corpus(count: int = 12) -> MemoryIndex:
    """Twelve bills that share a recital and nothing else.

    Exactly one of them carries the model clause, which is what makes the clause
    evidence of copying rather than another piece of boilerplate.
    """

    index = MemoryIndex()
    for position in range(count):
        if position == 1:
            tail = MODEL_CLAUSE
        elif position == 2:
            tail = LOCAL_CLAUSE
        else:
            subject = SUBJECTS[position % len(SUBJECTS)]
            tail = (
                f"The district shall keep a register of {subject} and shall report\n"
                f"annually to the county commissioners on the disposition of every\n"
                f"application concerning {subject} received during the year."
            )
        body = f"{RECITAL}\n\n(b)  {tail}"
        index.add_all(
            fingerprints(make_bill(jurisdiction="wy", number=f"HB{position:04d}", body=body))
        )
    return index


def test_a_default_matcher_has_no_filter() -> None:
    matcher = Matcher(MemoryIndex())
    assert len(matcher.boilerplate) == 0


def test_over_learns_a_filter_from_the_index() -> None:
    index = corpus()
    matcher = Matcher.over(index, min_segments=3)
    assert len(matcher.boilerplate) > 0
    assert matcher.boilerplate.segments_seen == len(index)


def test_learning_from_an_empty_index_suppresses_nothing() -> None:
    matcher = Matcher.over(MemoryIndex())
    assert len(matcher.boilerplate) == 0


def test_the_shared_recital_is_suppressed() -> None:
    index = corpus()
    learned = learn_boilerplate(iter(index), max_ratio=0.2, min_segments=3)
    probe = make_bill(jurisdiction="mt", number="HB0311", body=f"{RECITAL}\n\n(b)  {LOCAL_CLAUSE}")
    recital = next(r for r in fingerprints(probe) if r.path == "1(a)")
    assert len(learned.keeps(recital)) < len(recital.fingerprint_values)


def test_filtering_reduces_the_matches_on_boilerplate_alone() -> None:
    index = corpus()
    body = f"{RECITAL}\n\n(b)  The county shall keep a register of well permits."
    probe = make_bill(jurisdiction="mt", number="HB0311", body=body)
    plain = Matcher(index).match_instrument(probe)
    filtered = Matcher.over(index, min_segments=3).match_instrument(probe)
    assert len(filtered) < len(plain)


def test_a_real_copy_survives_the_filter() -> None:
    index = corpus()
    body = f"{RECITAL}\n\n(b)  {MODEL_CLAUSE}"
    probe = make_bill(jurisdiction="mt", number="HB0311", body=body)
    matches = Matcher.over(index, min_segments=3).match_instrument(probe)
    assert matches


def test_a_segment_that_is_all_boilerplate_matches_nothing() -> None:
    index = corpus()
    everything = BoilerplateFilter(
        suppressed=frozenset(value for record in index for value in record.fingerprint_values)
    )
    probe = make_bill(jurisdiction="mt", number="HB0311", body=MODEL_CLAUSE)
    matcher = Matcher(index, boilerplate=everything)
    assert matcher.match_instrument(probe) == []


def test_an_explicit_filter_is_kept() -> None:
    given = BoilerplateFilter(suppressed=frozenset({1, 2, 3}))
    assert Matcher(MemoryIndex(), boilerplate=given).boilerplate is given

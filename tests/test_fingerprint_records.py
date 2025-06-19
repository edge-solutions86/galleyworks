from __future__ import annotations

from lexdrift.config import EngineConfig, ShingleConfig
from lexdrift.fingerprint.minhash import jaccard_estimate
from lexdrift.fingerprint.records import Fingerprinter
from lexdrift.ingest.base import SourceDocument
from lexdrift.ingest.enrolled import EnrolledReader
from lexdrift.models import Instrument

HINTS = {"jurisdiction": "wy", "session": "2025", "number": "HB0142"}


def read(text: str, **extra: object) -> Instrument:
    return EnrolledReader().read(
        SourceDocument(text=text, origin="t.txt", hints={**HINTS, **extra})
    )


def bill(body: str, number: str = "HB0142") -> Instrument:
    text = (
        "AN ACT relating to water rights.\n\n"
        "Be It Enacted by the Legislature of the State of Wyoming:\n\n"
        "Section 1.  W.S. 41-3-101 is amended to read:\n\n"
        f"{body}\n"
    )
    return read(text, number=number)


BODY = (
    "(a)  The board shall publish a schedule of appropriations not later than\n"
    "sixty (60) days after the close of each irrigation season, and shall include\n"
    "the priority date of each appropriation in the schedule it publishes."
)


def test_every_body_segment_gets_a_record() -> None:
    instrument = bill(BODY)
    records = Fingerprinter().fingerprint(instrument)
    assert {record.path for record in records} == {"1", "1(a)"}


def test_records_carry_the_instrument_key() -> None:
    records = Fingerprinter().fingerprint(bill(BODY))
    assert all(record.key.slug == "wy/2025/lower/HB0142" for record in records)


def test_token_counts_are_positive() -> None:
    records = Fingerprinter().fingerprint(bill(BODY))
    assert all(record.token_count > 10 for record in records)


def test_fingerprint_spans_land_in_the_raw_text() -> None:
    instrument = bill(BODY)
    for record in Fingerprinter().fingerprint(instrument):
        for shingle in record.selected:
            assert shingle.span.end <= len(instrument.raw_text)
            assert shingle.span.slice_of(instrument.raw_text).strip()


def test_fingerprint_spans_quote_real_words() -> None:
    instrument = bill(BODY)
    record = next(r for r in Fingerprinter().fingerprint(instrument) if r.path == "1(a)")
    quoted = " ".join(s.span.slice_of(instrument.raw_text) for s in record.selected)
    assert "board" in quoted or "schedule" in quoted or "appropriation" in quoted


def test_canonical_text_is_folded() -> None:
    record = Fingerprinter().fingerprint(bill(BODY))[0]
    assert "60 days" in record.canonical
    assert "cite wy 41-3-101" in record.canonical


def test_winnowing_keeps_a_fraction_of_the_shingles() -> None:
    instrument = bill(BODY)
    record = next(r for r in Fingerprinter().fingerprint(instrument) if r.path == "1(a)")
    assert 0 < len(record.selected) < record.token_count


def test_identical_bodies_share_their_signature() -> None:
    left = Fingerprinter().fingerprint(bill(BODY))[0]
    right = Fingerprinter().fingerprint(bill(BODY, number="HB0999"))[0]
    assert left.signature == right.signature
    assert left.simhash == right.simhash


def test_a_reworded_body_diverges() -> None:
    other = BODY.replace("sixty (60) days", "ninety (90) days").replace(
        "priority date", "diversion point"
    )
    left = Fingerprinter().fingerprint(bill(BODY))[0]
    right = Fingerprinter().fingerprint(bill(other))[0]
    assert left.signature != right.signature
    assert 0.0 < jaccard_estimate(left.signature, right.signature) < 1.0


def test_fingerprint_values_and_lookup() -> None:
    record = Fingerprinter().fingerprint(bill(BODY))[0]
    value = record.fingerprint_values[0]
    assert record.span_of(value) is not None
    assert record.span_of(0) is None


def test_the_digest_follows_the_configuration() -> None:
    plain = Fingerprinter()
    wider = Fingerprinter(EngineConfig(shingle=ShingleConfig(width=9)))
    assert plain.digest != wider.digest


def test_a_narrow_body_still_fingerprints() -> None:
    records = Fingerprinter().fingerprint(bill("(a)  The board shall act promptly."))
    assert records
    assert all(record.selected for record in records)

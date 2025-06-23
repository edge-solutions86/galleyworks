from __future__ import annotations

import pytest

from lexdrift.errors import StoreError
from lexdrift.fingerprint.codec import (
    CODEC_VERSION,
    MAGIC,
    decode_record,
    encode_record,
    strip_canonical,
)
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.fingerprint.shingle import Shingle
from lexdrift.identity import Chamber, InstrumentKey
from lexdrift.util.spans import TextSpan


def record(**overrides: object) -> SegmentFingerprint:
    base = {
        "key": InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"),
        "segment_index": 4,
        "path": "1(a)(ii)",
        "token_count": 128,
        "signature": (1, 2, 3, 0xFFFFFFFFFFFFFFFF),
        "simhash": 0x0123456789ABCDEF,
        "selected": (
            Shingle(value=99, position=0, span=TextSpan(10, 40)),
            Shingle(value=1 << 63, position=7, span=TextSpan(41, 80)),
        ),
        "canonical": "the board shall publish",
    }
    base.update(overrides)
    return SegmentFingerprint(**base)  # type: ignore[arg-type]


def test_a_record_round_trips() -> None:
    decoded = decode_record(encode_record(record()))
    assert decoded == strip_canonical(record())


def test_the_canonical_text_is_not_stored() -> None:
    assert decode_record(encode_record(record())).canonical == ""


def test_the_blob_starts_with_the_magic_and_version() -> None:
    blob = encode_record(record())
    assert blob[:4] == MAGIC
    assert blob[4] == CODEC_VERSION


def test_an_empty_selection_round_trips() -> None:
    decoded = decode_record(encode_record(record(selected=())))
    assert decoded.selected == ()


def test_an_empty_signature_round_trips() -> None:
    decoded = decode_record(encode_record(record(signature=())))
    assert decoded.signature == ()


def test_full_width_values_survive() -> None:
    wide = record(simhash=0xFFFFFFFFFFFFFFFF, signature=(0xFFFFFFFFFFFFFFFF,))
    assert decode_record(encode_record(wide)).simhash == 0xFFFFFFFFFFFFFFFF


def test_shingle_spans_survive() -> None:
    decoded = decode_record(encode_record(record()))
    assert decoded.selected[1].span == TextSpan(41, 80)
    assert decoded.selected[1].position == 7


def test_a_short_blob_is_rejected() -> None:
    with pytest.raises(StoreError, match="too short"):
        decode_record(b"LX")


def test_a_foreign_magic_is_rejected() -> None:
    blob = bytearray(encode_record(record()))
    blob[0:4] = b"XXXX"
    with pytest.raises(StoreError, match="wrong magic"):
        decode_record(bytes(blob))


def test_another_version_is_rejected() -> None:
    blob = bytearray(encode_record(record()))
    blob[4] = CODEC_VERSION + 1
    with pytest.raises(StoreError, match="another codec version") as caught:
        decode_record(bytes(blob))
    assert caught.value.context["expected"] == CODEC_VERSION


def test_reserved_flags_are_rejected() -> None:
    blob = bytearray(encode_record(record()))
    blob[5] = 1
    with pytest.raises(StoreError, match="reserved flags"):
        decode_record(bytes(blob))


def test_trailing_bytes_are_rejected() -> None:
    with pytest.raises(StoreError, match="trailing bytes"):
        decode_record(encode_record(record()) + b"\x00")


def test_a_long_path_is_rejected() -> None:
    with pytest.raises(StoreError, match="too long to encode"):
        encode_record(record(path="x" * 70000))


def test_unicode_survives_the_round_trip() -> None:
    decoded = decode_record(encode_record(record(path="1(a)")))
    assert decoded.path == "1(a)"
    assert decoded.key.slug == "wy/2025/lower/HB0142"

"""Binary encoding for fingerprint records.

The index stores records as blobs, so the layout is versioned from the first
release. A reader that meets a version it does not know refuses rather than
decoding a shorter record into a longer struct and reporting nonsense.

Layout, all integers big endian:

    magic       4 bytes, ``LXDF``
    version     1 byte
    flags       1 byte, reserved, must be zero
    key         u16 length + utf-8
    path        u16 length + utf-8
    segment     u32
    tokens      u32
    simhash     u64
    signature   u16 count + count * u64
    selected    u32 count + count * (u64 value, u32 position, u32 start, u32 end)

The canonical text is deliberately not stored. It is derivable from the raw
document and it is by far the largest field.
"""

from __future__ import annotations

import struct
from dataclasses import replace

from lexdrift.errors import StoreError
from lexdrift.fingerprint.records import SegmentFingerprint
from lexdrift.fingerprint.shingle import Shingle
from lexdrift.identity import InstrumentKey
from lexdrift.util.spans import TextSpan

__all__ = ["CODEC_VERSION", "MAGIC", "decode_record", "encode_record"]

MAGIC = b"LXDF"
CODEC_VERSION = 1

_HEADER = struct.Struct(">4sBB")
_SHORT = struct.Struct(">H")
_LONG = struct.Struct(">I")
_WORD = struct.Struct(">Q")
_BODY = struct.Struct(">IIQ")
_SHINGLE = struct.Struct(">QIII")


def _put_text(out: bytearray, value: str) -> None:
    blob = value.encode("utf-8")
    if len(blob) > 0xFFFF:
        raise StoreError("fingerprint field is too long to encode", size=len(blob))
    out += _SHORT.pack(len(blob))
    out += blob


def _take_text(blob: bytes, offset: int) -> tuple[str, int]:
    (size,) = _SHORT.unpack_from(blob, offset)
    offset += _SHORT.size
    text = blob[offset : offset + size].decode("utf-8")
    return text, offset + size


def encode_record(record: SegmentFingerprint) -> bytes:
    """Serialize one fingerprint record."""

    out = bytearray(_HEADER.pack(MAGIC, CODEC_VERSION, 0))
    _put_text(out, record.key.slug)
    _put_text(out, record.path)
    out += _BODY.pack(record.segment_index, record.token_count, record.simhash)
    out += _SHORT.pack(len(record.signature))
    for value in record.signature:
        out += _WORD.pack(value)
    out += _LONG.pack(len(record.selected))
    for shingle in record.selected:
        out += _SHINGLE.pack(shingle.value, shingle.position, shingle.span.start, shingle.span.end)
    return bytes(out)


def decode_record(blob: bytes) -> SegmentFingerprint:
    """Rebuild a record written by :func:`encode_record`."""

    if len(blob) < _HEADER.size:
        raise StoreError("fingerprint blob is too short to hold a header", size=len(blob))
    magic, version, flags = _HEADER.unpack_from(blob, 0)
    if magic != MAGIC:
        raise StoreError("fingerprint blob has the wrong magic", magic=magic.hex())
    if version != CODEC_VERSION:
        raise StoreError(
            "fingerprint blob was written by another codec version",
            found=version,
            expected=CODEC_VERSION,
        )
    if flags:
        raise StoreError("fingerprint blob sets reserved flags", flags=flags)
    offset = _HEADER.size
    slug, offset = _take_text(blob, offset)
    path, offset = _take_text(blob, offset)
    segment_index, token_count, simhash = _BODY.unpack_from(blob, offset)
    offset += _BODY.size
    (width,) = _SHORT.unpack_from(blob, offset)
    offset += _SHORT.size
    signature: list[int] = []
    for _ in range(width):
        (value,) = _WORD.unpack_from(blob, offset)
        signature.append(value)
        offset += _WORD.size
    (count,) = _LONG.unpack_from(blob, offset)
    offset += _LONG.size
    selected: list[Shingle] = []
    for _ in range(count):
        value, position, start, end = _SHINGLE.unpack_from(blob, offset)
        selected.append(Shingle(value=value, position=position, span=TextSpan(start, end)))
        offset += _SHINGLE.size
    if offset != len(blob):
        raise StoreError("fingerprint blob has trailing bytes", extra=len(blob) - offset)
    return SegmentFingerprint(
        key=InstrumentKey.parse(slug),
        segment_index=segment_index,
        path=path,
        token_count=token_count,
        signature=tuple(signature),
        simhash=simhash,
        selected=tuple(selected),
        canonical="",
    )


def strip_canonical(record: SegmentFingerprint) -> SegmentFingerprint:
    """Drop the canonical text, which the codec never writes."""

    return replace(record, canonical="")

"""Label sequences, and the rule that resolves ``(i)``.

Legislative drafting nests labels alphabetically then in lower roman: ``(a)``,
``(b)`` ... then ``(i)``, ``(ii)``, ``(iii)``. Seven labels are legal in both
alphabets, and ``(i)`` is the one that actually happens, because it is both the
ninth letter and roman one.

Nothing about the line resolves it. What resolves it is the sequence already
open: a subsection list that has reached ``(h)`` makes ``(i)`` its continuation,
and a subsection list at ``(a)`` makes ``(i)`` the start of a nested roman list.
"""

from __future__ import annotations

from lexdrift.errors import ParseError

__all__ = [
    "ALPHA",
    "ROMAN_VALUES",
    "alpha_successor",
    "is_roman",
    "roman_successor",
    "roman_value",
    "to_roman",
]

ALPHA = "abcdefghijklmnopqrstuvwxyz"
ROMAN_VALUES: dict[str, int] = {"i": 1, "v": 5, "x": 10, "l": 50, "c": 100, "d": 500, "m": 1000}
_ROMAN_TABLE: tuple[tuple[int, str], ...] = (
    (1000, "m"),
    (900, "cm"),
    (500, "d"),
    (400, "cd"),
    (100, "c"),
    (90, "xc"),
    (50, "l"),
    (40, "xl"),
    (10, "x"),
    (9, "ix"),
    (5, "v"),
    (4, "iv"),
    (1, "i"),
)


def alpha_successor(label: str) -> str:
    """The label that follows ``label`` in the ``a, b, ... z, aa, bb`` sequence."""

    lowered = label.lower()
    if not lowered or any(char not in ALPHA for char in lowered):
        raise ParseError("not an alphabetic label", line=0, label=label)
    if len(set(lowered)) != 1:
        raise ParseError("alphabetic labels repeat a single letter", line=0, label=label)
    letter = lowered[0]
    if letter == "z":
        return "a" * (len(lowered) + 1)
    return ALPHA[ALPHA.index(letter) + 1] * len(lowered)


def is_roman(label: str) -> bool:
    """True when every character is a roman digit."""

    lowered = label.lower()
    return bool(lowered) and all(char in ROMAN_VALUES for char in lowered)


def roman_value(label: str) -> int:
    """Decode a lower roman label using subtractive notation."""

    lowered = label.lower()
    if not is_roman(lowered):
        raise ParseError("not a roman label", line=0, label=label)
    total = 0
    previous = 0
    for char in reversed(lowered):
        current = ROMAN_VALUES[char]
        if current < previous:
            total -= current
        else:
            total += current
            previous = current
    if to_roman(total) != lowered:
        raise ParseError("roman label is not in canonical form", line=0, label=label)
    return total


def to_roman(value: int) -> str:
    """Encode a positive integer as a lower roman label."""

    if value <= 0:
        raise ParseError("roman labels start at one", line=0, value=value)
    out: list[str] = []
    remaining = value
    for magnitude, glyph in _ROMAN_TABLE:
        while remaining >= magnitude:
            out.append(glyph)
            remaining -= magnitude
    return "".join(out)


def roman_successor(label: str) -> str:
    """The roman label that follows ``label``."""

    return to_roman(roman_value(label) + 1)

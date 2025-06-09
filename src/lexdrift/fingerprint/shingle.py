"""Overlapping windows over canonical text.

A shingle is a fixed-width window of the token stream, hashed. Two documents
share a shingle when they share that exact run of tokens, so the shingle set is
what containment and Jaccard are actually computed over.

Every shingle keeps the span it covers, because a match has to be reported as a
character range and not as a bag of hashes.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from lexdrift.canon.tokens import Token, tokenize, words_only
from lexdrift.config import ShingleConfig, ShingleMode
from lexdrift.errors import MatchError
from lexdrift.fingerprint.hashing import hash_ngram, hash_token
from lexdrift.util.spans import TextSpan

__all__ = [
    "Shingle",
    "char_shingles",
    "shingles_for",
    "word_shingles",
]


@dataclass(frozen=True, slots=True)
class Shingle:
    """One hashed window plus where it sits."""

    value: int
    position: int
    span: TextSpan

    def __lt__(self, other: Shingle) -> bool:
        return (self.position, self.value) < (other.position, other.value)


def word_shingles(tokens: Sequence[Token], width: int) -> list[Shingle]:
    """Hash every window of ``width`` consecutive tokens."""

    if width < 1:
        raise MatchError("shingle width must be at least one", width=width)
    if len(tokens) < width:
        return []
    out: list[Shingle] = []
    for start in range(len(tokens) - width + 1):
        window = tokens[start : start + width]
        out.append(
            Shingle(
                value=hash_ngram([token.lowered for token in window]),
                position=start,
                span=TextSpan(window[0].span.start, window[-1].span.end),
            )
        )
    return out


def char_shingles(text: str, width: int, *, offset: int = 0) -> list[Shingle]:
    """Hash every window of ``width`` consecutive characters."""

    if width < 1:
        raise MatchError("shingle width must be at least one", width=width)
    if len(text) < width:
        return []
    return [
        Shingle(
            value=hash_token(text[start : start + width]),
            position=start,
            span=TextSpan(start + offset, start + width + offset),
        )
        for start in range(len(text) - width + 1)
    ]


def shingles_for(text: str, config: ShingleConfig, *, offset: int = 0) -> list[Shingle]:
    """Shingle ``text`` the way ``config`` says to."""

    if config.mode is ShingleMode.CHARACTER:
        return char_shingles(text, config.width, offset=offset)
    tokens = list(words_only(tokenize(text, offset=offset)))
    return word_shingles(tokens, config.width)

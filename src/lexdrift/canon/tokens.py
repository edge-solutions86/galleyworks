"""Tokenizer for legislative prose.

The tokenizer keeps offsets. Everything downstream, from shingling to the final
report, needs to point back at a character range in the raw document, so a token
that forgets where it came from is useless to us.

It is deliberately conservative about what it joins: ``sixty-five`` is one word
token, ``W.S. 41-3-101`` is three tokens plus punctuation, and the section sign
is its own token so a citation rule can find it later.
"""

from __future__ import annotations

import re
from collections.abc import Iterator, Sequence
from dataclasses import dataclass
from enum import StrEnum

from lexdrift.util.spans import TextSpan

__all__ = [
    "Token",
    "TokenKind",
    "tokenize",
    "words_only",
]


class TokenKind(StrEnum):
    WORD = "word"
    NUMBER = "number"
    PUNCT = "punct"
    SECTION_SIGN = "section_sign"


@dataclass(frozen=True, slots=True)
class Token:
    """One lexical unit plus its span in the source text."""

    text: str
    span: TextSpan
    kind: TokenKind

    @property
    def is_word(self) -> bool:
        return self.kind is TokenKind.WORD

    @property
    def lowered(self) -> str:
        return self.text.lower()


_WORD = r"[^\W\d_](?:[^\W\d_]|['’‐-]?[^\W\d_])*"
_NUMBER = r"\d+(?:[.,\-/]\d+)*(?:st|nd|rd|th)?"
_TOKEN_RE = re.compile(
    rf"(?P<section>§+)|(?P<word>{_WORD})|(?P<number>{_NUMBER})|(?P<punct>[^\s])",
    re.UNICODE,
)

_KIND_BY_GROUP = {
    "section": TokenKind.SECTION_SIGN,
    "word": TokenKind.WORD,
    "number": TokenKind.NUMBER,
    "punct": TokenKind.PUNCT,
}


def tokenize(text: str, *, offset: int = 0) -> list[Token]:
    """Split ``text`` into tokens whose spans are shifted by ``offset``."""

    if offset < 0:
        raise ValueError(f"offset must be non-negative, got {offset}")
    tokens: list[Token] = []
    for match in _TOKEN_RE.finditer(text):
        group = match.lastgroup
        if group is None:  # pragma: no cover - the pattern always names a group
            continue
        start, end = match.span()
        tokens.append(
            Token(
                text=match.group(),
                span=TextSpan(start + offset, end + offset),
                kind=_KIND_BY_GROUP[group],
            )
        )
    return tokens


def words_only(tokens: Sequence[Token]) -> Iterator[Token]:
    """Yield the tokens a similarity comparison should see."""

    for token in tokens:
        if token.kind in (TokenKind.WORD, TokenKind.NUMBER):
            yield token

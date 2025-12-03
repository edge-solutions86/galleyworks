"""Function words, and the two places they should and should not be dropped.

Shingles keep stopwords. A five-token window of legislative prose is mostly
function words, and dropping them merges ``the board shall notify the applicant``
with ``board notify applicant``, which is a different clause. Word order and the
small words are exactly what distinguishes copied drafting from parallel
drafting, so the fingerprinting path never sees this list.

SimHash weighting is the other place, and there it matters a great deal. An
unweighted signature of a legislative segment is dominated by ``the``, ``of``
and ``shall``, which every segment in the corpus shares, so every pair lands a
few bits apart and the cheap filter filters nothing.

The list is deliberately short and deliberately includes the modal verbs. ``may``
and ``shall`` carry the difference between permission and duty, so they are
weighted down rather than removed: :func:`is_stopword` is advice about weight,
never about meaning.
"""

from __future__ import annotations

from collections.abc import Iterable, Iterator

__all__ = ["STOPWORDS", "content_ratio", "content_tokens", "is_stopword"]

STOPWORDS = frozenset(
    """
    a an and any are as at be been being but by
    each for from has have if in into is it its
    may must no nor not of on or other otherwise
    per provided pursuant same shall should so subject such
    that the their them then there these this those to
    under upon was were which who whom whose will with within without
    """.split()
)


def is_stopword(token: str) -> bool:
    """True when ``token`` is a function word."""

    return token.lower() in STOPWORDS


def content_tokens(tokens: Iterable[str]) -> Iterator[str]:
    """Yield the tokens that carry subject matter."""

    for token in tokens:
        if not is_stopword(token):
            yield token


def content_ratio(tokens: Iterable[str]) -> float:
    """Fraction of ``tokens`` that are not function words.

    A segment under about a fifth content is a recital or a caption, and a match
    against one says nothing about copying.
    """

    total = 0
    content = 0
    for token in tokens:
        total += 1
        if not is_stopword(token):
            content += 1
    return 0.0 if total == 0 else content / total

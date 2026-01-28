"""Spelling variants that mean the same thing.

Legislative drafting manuals disagree, and the same clause is re-typed with
``judgement`` for ``judgment`` and ``per centum`` for ``percent`` as it crosses a
state line. Those differences are re-typing, not redrafting, and a fingerprinter
that treats them as substantive reports a copied clause as original.

Only pairs where the two forms are interchangeable in a statute are listed.
``insure`` and ``ensure`` are not: they are different words in an insurance code
and folding them would erase a distinction the text is making on purpose.
"""

from __future__ import annotations

import re
from collections.abc import Iterable

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = ["VARIANTS", "VariantSpellingRule", "canonical_spelling"]

VARIANTS: dict[str, str] = {
    "acknowledgement": "acknowledgment",
    "adviser": "advisor",
    "amongst": "among",
    "annulment": "annulment",
    "authorise": "authorize",
    "authorised": "authorized",
    "behaviour": "behavior",
    "cancelled": "canceled",
    "colour": "color",
    "defence": "defense",
    "enrolment": "enrollment",
    "favour": "favor",
    "fulfil": "fulfill",
    "instalment": "installment",
    "judgement": "judgment",
    "labour": "labor",
    "licence": "license",
    "neighbouring": "neighboring",
    "offence": "offense",
    "organisation": "organization",
    "practise": "practice",
    "programme": "program",
    "recognise": "recognize",
    "recognised": "recognized",
    "shew": "show",
    "travelling": "traveling",
    "whilst": "while",
}

_WORD_RE = re.compile(r"[A-Za-z]+")


def canonical_spelling(word: str) -> str:
    """The preferred form of ``word``, or the word itself."""

    return VARIANTS.get(word.lower(), word)


class VariantSpellingRule:
    """Folds interchangeable spellings to one form."""

    name = "variant-spelling"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.fold_variants:
            return
        for match in _WORD_RE.finditer(text):
            word = match.group()
            preferred = VARIANTS.get(word.lower())
            if preferred is None or preferred == word:
                continue
            yield Edit(TextSpan(match.start(), match.end()), preferred)

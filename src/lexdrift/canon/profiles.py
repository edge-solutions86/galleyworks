"""Rule order, and the presets that switch parts of it off.

The order is load bearing and is asserted by the tests:

1. unicode folding, so every later pattern sees ASCII punctuation
2. whitespace, so a pattern can rely on single spaces
3. defined terms, which needs the quotation marks still in place
4. citations, before numbers can read ``41-3-101`` as three numerals
5. cross-references, after citations so a numbered one is already claimed
6. numerals
7. enumeration labels
8. the enacting clause
9. case folding, because the rules above match on capitalisation
10. variant spellings, last, on text that is already lowercase

A profile is a :class:`~lexdrift.config.CanonConfig` with a name. It never
reorders rules; it only enables and disables them, which keeps one index
comparable with another built from a different profile as long as the digest
matches.
"""

from __future__ import annotations

from lexdrift.canon.citations import CitationRule
from lexdrift.canon.crossrefs import CrossReferenceRule
from lexdrift.canon.defined_terms import DefinedTermRule
from lexdrift.canon.numerals import NumeralRule
from lexdrift.canon.pipeline import CanonPipeline, CanonResult, Rule
from lexdrift.canon.structure_rules import EnactingClauseRule, EnumerationRule
from lexdrift.canon.text_rules import CaseFoldRule, UnicodeFoldRule, WhitespaceRule
from lexdrift.canon.variants import VariantSpellingRule
from lexdrift.config import CanonConfig
from lexdrift.errors import ConfigError

__all__ = [
    "PROFILES",
    "build_pipeline",
    "canonicalize",
    "default_rules",
    "profile",
]

PROFILES: dict[str, CanonConfig] = {
    "default": CanonConfig(),
    # Municipal codes renumber wholesale between adoptions, and their ordinance
    # numbers are not statutory citations, so citation folding is off.
    "municipal": CanonConfig(profile="municipal", normalize_citations=False),
    # Federal drafting keeps its enacting clause short and does not repeat it,
    # and the doubled-numeral convention is rare.
    "federal": CanonConfig(profile="federal", drop_enacting_clause=False, normalize_numbers=False),
    # A verbatim profile for diffing two prints of the same act.
    "verbatim": CanonConfig(
        profile="verbatim",
        fold_case=False,
        fold_variants=False,
        normalize_numbers=False,
        normalize_citations=False,
        drop_enumeration_labels=False,
        drop_enacting_clause=False,
    ),
}


def default_rules() -> list[Rule]:
    """The canonical rule order."""

    return [
        UnicodeFoldRule(),
        WhitespaceRule(),
        DefinedTermRule(),
        CitationRule(),
        CrossReferenceRule(),
        NumeralRule(),
        EnumerationRule(),
        EnactingClauseRule(),
        CaseFoldRule(),
        VariantSpellingRule(),
    ]


def build_pipeline() -> CanonPipeline:
    """A pipeline holding every rule; the config decides which ones act."""

    return CanonPipeline(default_rules())


def profile(name: str) -> CanonConfig:
    """Look up a named profile."""

    try:
        return PROFILES[name]
    except KeyError as exc:
        raise ConfigError(
            "unknown canonicalization profile",
            profile=name,
            known=",".join(sorted(PROFILES)),
        ) from exc


def canonicalize(text: str, config: CanonConfig | None = None) -> CanonResult:
    """Run the default pipeline over ``text``."""

    return build_pipeline().run(text, config)

"""Saying why a match was reported, in words.

A containment of 0.62 is not an argument. What persuades a reader is the shape
of the evidence: how many separate passages, how long the longest one is,
whether they run in the same order in both documents, and whether the language
is distinctive or the kind of phrasing every act in the state uses.

The explanation is derived, never stored. It has to stay in step with the
thresholds actually applied, and the only way to guarantee that is to compute it
from the same config the matcher ran under.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

from lexdrift.config import MatchConfig
from lexdrift.match.engine import SegmentMatch

__all__ = ["Confidence", "Explanation", "explain"]


class Confidence(StrEnum):
    """How strong the evidence is, on the evidence alone."""

    WEAK = "weak"
    MODERATE = "moderate"
    STRONG = "strong"


@dataclass(frozen=True, slots=True)
class Explanation:
    """Why one match was reported."""

    confidence: Confidence
    headline: str
    reasons: tuple[str, ...]

    @property
    def is_strong(self) -> bool:
        return self.confidence is Confidence.STRONG


def _ordered_alike(match: SegmentMatch) -> bool:
    """True when the passages appear in the same order in both documents."""

    if len(match.passages) < 2:
        return True
    by_query = sorted(match.passages, key=lambda passage: passage.query.start)
    starts = [passage.other.start for passage in by_query]
    return starts == sorted(starts)


def explain(match: SegmentMatch, config: MatchConfig | None = None) -> Explanation:
    """Describe the evidence behind ``match``."""

    settings = config or MatchConfig()
    containment = match.score.best_containment
    longest = match.longest_passage
    reasons: list[str] = [
        f"{containment:.0%} of the smaller fingerprint set is shared",
        f"the longest shared passage runs {longest} characters",
    ]
    score = 0
    if containment >= 0.85:
        score += 2
    elif containment >= max(settings.min_containment, 0.5):
        score += 1
    if longest >= settings.min_passage_chars * 4:
        score += 2
        reasons.append("that passage is several times the reporting floor")
    elif longest >= settings.min_passage_chars * 2:
        score += 1
    if match.passage_count >= 3:
        score += 1
        reasons.append(f"the text matches in {match.passage_count} separate places")
    if _ordered_alike(match):
        if match.passage_count >= 2:
            reasons.append("the passages appear in the same order in both documents")
    else:
        score -= 1
        reasons.append("the passages appear in a different order, which weakens the case")
    if match.crosses_jurisdictions:
        reasons.append(
            f"the two instruments are from different jurisdictions "
            f"({match.query.key.jurisdiction} and {match.other.key.jurisdiction})"
        )
    else:
        reasons.append("both instruments are from the same jurisdiction, where reuse is routine")
        score -= 1
    if score >= 4:
        confidence = Confidence.STRONG
    elif score >= 2:
        confidence = Confidence.MODERATE
    else:
        confidence = Confidence.WEAK
    headline = (
        f"{confidence.value} evidence that {match.query_path} reuses "
        f"{match.other.key.slug} {match.other_path}"
    )
    return Explanation(confidence=confidence, headline=headline, reasons=tuple(reasons))

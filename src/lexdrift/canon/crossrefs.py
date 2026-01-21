"""Internal cross-references.

``this act``, ``this section``, ``the preceding subsection``: every act says
these and each one means something different in each act. Left alone they are
strong shared fingerprints that carry no information about copying, because two
unrelated bills both contain "the provisions of this act".

They are folded to a marker rather than deleted. Deleting them would join the
words on either side into a shingle that never appeared in either document, and
that invented shingle can then match another invented one somewhere else.

A reference to a *numbered* section is left alone. ``section 4 of this act``
identifies a provision, and two acts that both point at their own section 4 are
saying different things about different text; the citation rule handles the ones
that name a statute.
"""

from __future__ import annotations

import re
from collections.abc import Iterable

from lexdrift.canon.pipeline import Edit
from lexdrift.config import CanonConfig
from lexdrift.util.spans import TextSpan

__all__ = ["CrossReferenceRule", "SELF_REFERENCE"]

SELF_REFERENCE = "xref"

_UNITS = (
    "act",
    "chapter",
    "article",
    "section",
    "subsection",
    "paragraph",
    "subparagraph",
    "title",
)
_UNIT = f"(?:{'|'.join(_UNITS)})"
_CROSSREF_RE = re.compile(
    rf"\b(?:this|that|the\s+(?:preceding|succeeding|following|same))\s+{_UNIT}s?\b",
    re.IGNORECASE,
)


class CrossReferenceRule:
    """Folds ``this act`` and its relatives to one marker."""

    name = "cross-references"

    def edits(self, text: str, config: CanonConfig) -> Iterable[Edit]:
        if not config.normalize_citations:
            return
        for match in _CROSSREF_RE.finditer(text):
            yield Edit(TextSpan(match.start(), match.end()), SELF_REFERENCE)

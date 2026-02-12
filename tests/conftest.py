from __future__ import annotations

from typing import Any

import pytest
from hypothesis import HealthCheck, settings

from lexdrift.ingest.base import SourceDocument

# CI machines are shared and slower than a laptop, so the deadline that is
# useful locally turns into a flaky failure there. The profile is registered
# once here rather than repeated on every property test.
settings.register_profile(
    "ci",
    max_examples=50,
    deadline=None,
    suppress_health_check=[HealthCheck.too_slow],
)
settings.register_profile("dev", max_examples=25, deadline=None)
settings.load_profile("ci")

WATER_BILL = """2025

STATE OF WYOMING                                   25LSO-0142

HOUSE BILL NO. HB0142

Water rights; publication schedule.

AN ACT relating to water rights; requiring the board to publish a schedule of
appropriations; specifying contents of the schedule; and providing for an
effective date.

Be It Enacted by the Legislature of the State of Wyoming:

Section 1.  W.S. 41-3-101 is amended to read:

41-3-101.  Schedule of appropriations.

(a)  The board shall publish a schedule of water rights not later than sixty
(60) days after the close of each irrigation season.

    (i)  The schedule shall include the priority date of each appropriation;

    (ii)  The schedule shall be published on the board's website.

(b)  This section does not apply to a temporary appropriation.

Section 2.  This act is effective July 1, 2025.
"""

DEFAULT_HINTS: dict[str, Any] = {
    "jurisdiction": "wy",
    "session": "2025",
    "number": "HB0142",
    "chamber": "lower",
}


@pytest.fixture
def water_bill_text() -> str:
    return WATER_BILL


@pytest.fixture
def water_bill() -> SourceDocument:
    return SourceDocument(text=WATER_BILL, origin="hb0142.txt", hints=dict(DEFAULT_HINTS))


@pytest.fixture
def make_source() -> object:
    def build(text: str, **hints: Any) -> SourceDocument:
        return SourceDocument(text=text, origin="t.txt", hints={**DEFAULT_HINTS, **hints})

    return build

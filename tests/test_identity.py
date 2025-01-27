from __future__ import annotations

import pytest

from lexdrift.errors import ConfigError
from lexdrift.identity import Chamber, GovernmentLevel, InstrumentKey, Jurisdiction


def make_jurisdiction(slug: str = "wy", parent: str | None = None) -> Jurisdiction:
    return Jurisdiction(slug=slug, name="Wyoming", level=GovernmentLevel.STATE, parent=parent)


def test_jurisdiction_accepts_a_plain_slug() -> None:
    jur = make_jurisdiction()
    assert jur.slug == "wy"
    assert jur.level is GovernmentLevel.STATE
    assert not jur.is_subordinate


@pytest.mark.parametrize("slug", ["", "WY", "-wy", "wy_west", "w" * 33])
def test_jurisdiction_rejects_bad_slugs(slug: str) -> None:
    with pytest.raises(ConfigError, match="valid identifier"):
        make_jurisdiction(slug=slug)


def test_jurisdiction_rejects_empty_name() -> None:
    with pytest.raises(ConfigError, match="name is empty"):
        Jurisdiction(slug="wy", name="   ", level=GovernmentLevel.STATE)


def test_jurisdiction_rejects_self_parent() -> None:
    with pytest.raises(ConfigError, match="its own parent"):
        make_jurisdiction(parent="wy")


def test_ancestry_walks_upward() -> None:
    city = Jurisdiction(
        slug="laramie", name="Laramie", level=GovernmentLevel.MUNICIPAL, parent="albany-county"
    )
    county = Jurisdiction(
        slug="albany-county", name="Albany County", level=GovernmentLevel.COUNTY, parent="wy"
    )
    state = make_jurisdiction()
    table = {j.slug: j for j in (city, county, state)}
    assert city.ancestry(table) == ["laramie", "albany-county", "wy"]


def test_ancestry_stops_at_an_unknown_parent() -> None:
    city = Jurisdiction(
        slug="laramie", name="Laramie", level=GovernmentLevel.MUNICIPAL, parent="nowhere"
    )
    assert city.ancestry({city.slug: city}) == ["laramie", "nowhere"]


def test_ancestry_detects_a_cycle() -> None:
    left = Jurisdiction(slug="a", name="A", level=GovernmentLevel.STATE, parent="b")
    right = Jurisdiction(slug="b", name="B", level=GovernmentLevel.STATE, parent="a")
    with pytest.raises(ConfigError, match="cycle"):
        left.ancestry({"a": left, "b": right})


def test_instrument_key_round_trips_through_its_slug() -> None:
    key = InstrumentKey("wy", "2025-general", Chamber.LOWER, "HB0142")
    assert key.slug == "wy/2025-general/lower/HB0142"
    assert InstrumentKey.parse(key.slug) == key
    assert str(key) == key.slug


def test_instrument_keys_sort_by_field_order() -> None:
    first = InstrumentKey("wy", "2024", Chamber.UPPER, "SF0001")
    second = InstrumentKey("wy", "2025", Chamber.LOWER, "HB0001")
    assert sorted([second, first]) == [first, second]


@pytest.mark.parametrize(
    "slug",
    ["wy/2025/lower", "wy//lower/HB1", "WY/2025/lower/HB1", "wy/2025/lower/", "wy/2025/lower/HB 1"],
)
def test_instrument_key_rejects_malformed_slugs(slug: str) -> None:
    with pytest.raises(ConfigError, match="does not parse"):
        InstrumentKey.parse(slug)


def test_instrument_key_rejects_unknown_chamber() -> None:
    with pytest.raises(ConfigError, match="unknown chamber"):
        InstrumentKey.parse("wy/2025/senate/HB1")


def test_instrument_key_validates_its_own_fields() -> None:
    with pytest.raises(ConfigError, match="session is malformed"):
        InstrumentKey("wy", "", Chamber.LOWER, "HB1")
    with pytest.raises(ConfigError, match="number is malformed"):
        InstrumentKey("wy", "2025", Chamber.LOWER, "")

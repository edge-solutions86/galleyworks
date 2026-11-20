package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityTest {

    private static Jurisdiction makeJurisdiction(String slug) {
        return new Jurisdiction(slug, "Wyoming", GovernmentLevel.STATE);
    }

    @Test
    void jurisdictionAcceptsAPlainSlug() {
        Jurisdiction jur = makeJurisdiction("wy");
        assertThat(jur.slug()).isEqualTo("wy");
        assertThat(jur.level()).isSameAs(GovernmentLevel.STATE);
        assertThat(jur.isSubordinate()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "WY", "-wy", "wy_west", "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww"})
    void jurisdictionRejectsBadSlugs(String slug) {
        assertThatThrownBy(() -> makeJurisdiction(slug))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("valid identifier");
    }

    @Test
    void jurisdictionRejectsEmptyName() {
        assertThatThrownBy(() -> new Jurisdiction("wy", "   ", GovernmentLevel.STATE))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("name is empty");
    }

    @Test
    void jurisdictionRejectsSelfParent() {
        assertThatThrownBy(() -> new Jurisdiction("wy", "Wyoming", GovernmentLevel.STATE, "wy"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("its own parent");
    }

    @Test
    void ancestryWalksUpward() {
        Jurisdiction city = new Jurisdiction("laramie", "Laramie", GovernmentLevel.MUNICIPAL, "albany-county");
        Jurisdiction county = new Jurisdiction("albany-county", "Albany County", GovernmentLevel.COUNTY, "wy");
        Jurisdiction state = makeJurisdiction("wy");
        Map<String, Jurisdiction> table = Map.of(city.slug(), city, county.slug(), county, state.slug(), state);
        assertThat(city.ancestry(table)).containsExactly("laramie", "albany-county", "wy");
    }

    @Test
    void ancestryStopsAtAnUnknownParent() {
        Jurisdiction city = new Jurisdiction("laramie", "Laramie", GovernmentLevel.MUNICIPAL, "nowhere");
        assertThat(city.ancestry(Map.of(city.slug(), city))).containsExactly("laramie", "nowhere");
    }

    @Test
    void ancestryDetectsACycle() {
        Jurisdiction left = new Jurisdiction("a", "A", GovernmentLevel.STATE, "b");
        Jurisdiction right = new Jurisdiction("b", "B", GovernmentLevel.STATE, "a");
        assertThatThrownBy(() -> left.ancestry(Map.of("a", left, "b", right)))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void instrumentKeyRoundTripsThroughItsSlug() {
        InstrumentKey key = new InstrumentKey("wy", "2025-general", Chamber.LOWER, "HB0142");
        assertThat(key.slug()).isEqualTo("wy/2025-general/lower/HB0142");
        assertThat(InstrumentKey.parse(key.slug())).isEqualTo(key);
        assertThat(key).hasToString(key.slug());
    }

    @Test
    void instrumentKeysSortByFieldOrder() {
        InstrumentKey first = new InstrumentKey("wy", "2024", Chamber.UPPER, "SF0001");
        InstrumentKey second = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0001");
        List<InstrumentKey> keys = new ArrayList<>(List.of(second, first));
        keys.sort(null);
        assertThat(keys).containsExactly(first, second);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"wy/2025/lower", "wy//lower/HB1", "WY/2025/lower/HB1", "wy/2025/lower/", "wy/2025/lower/HB 1"})
    void instrumentKeyRejectsMalformedSlugs(String slug) {
        assertThatThrownBy(() -> InstrumentKey.parse(slug))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("does not parse");
    }

    @Test
    void instrumentKeyRejectsUnknownChamber() {
        assertThatThrownBy(() -> InstrumentKey.parse("wy/2025/senate/HB1"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unknown chamber");
    }

    @Test
    void instrumentKeyValidatesItsOwnFields() {
        assertThatThrownBy(() -> new InstrumentKey("wy", "", Chamber.LOWER, "HB1"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("session is malformed");
        assertThatThrownBy(() -> new InstrumentKey("wy", "2025", Chamber.LOWER, ""))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("number is malformed");
    }
}

package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.diffusion.DiffusionMetrics;
import io.github.rkaushikdev.galleyworks.diffusion.Lineage;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GoldenTest {

    private static final OffsetDateTime FIXED = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

    private static RunResult run() {
        return new Engine().runOver(GoldenCorpus.goldenInstruments(), Optional.of(FIXED));
    }

    private static InstrumentReport report(RunResult result, String slug) {
        return result.reportFor(slug).orElseThrow();
    }

    private static Set<String> otherJurisdictions(InstrumentReport report) {
        return report.matches().stream()
                .map(match -> match.otherKey().jurisdiction())
                .collect(Collectors.toSet());
    }

    private static List<Lineage> families(RunResult result) {
        return Lineage.lineages(result.graph()).stream()
                .filter(family -> family.size() > 1)
                .toList();
    }

    private static Lineage stateFamily(RunResult result) {
        return Lineage.lineages(result.graph()).stream()
                .filter(family -> family.size() == 3)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void theCorpusParses() {
        assertThat(GoldenCorpus.goldenInstruments().stream()
                        .map(instrument -> instrument.key().slug()))
                .containsExactly(
                        "wy/2023/lower/HB0142",
                        "mt/2024/lower/HB0311",
                        "nd/2025/lower/HB0044",
                        "co/2025/upper/SB0007",
                        "laramie/2024/council/2024-31",
                        "cheyenne/2025/council/2025-08");
    }

    @Test
    void effectiveDatesAreRead() {
        assertThat(GoldenCorpus.goldenInstruments().stream()
                        .map(instrument -> instrument.effectiveDate().orElseThrow()))
                .containsExactly(
                        LocalDate.of(2023, 7, 1),
                        LocalDate.of(2024, 10, 1),
                        LocalDate.of(2025, 8, 1),
                        LocalDate.of(2025, 9, 1),
                        LocalDate.of(2024, 4, 1),
                        LocalDate.of(2025, 7, 1));
    }

    @Test
    void theWyomingBillHasTheExpectedStructure() {
        Instrument wyoming = GoldenCorpus.goldenInstruments().getFirst();
        assertThat(wyoming.bodySegments().stream().map(segment -> segment.path().printed()))
                .containsExactly("1", "1(a)", "1(b)", "1(b)(i)", "1(b)(ii)", "2");
    }

    @Test
    void montanaReusesWyoming() {
        InstrumentReport report = report(run(), "mt/2024/lower/HB0311");
        assertThat(report.isEmpty()).isFalse();
        assertThat(otherJurisdictions(report)).containsExactly("wy");
    }

    @Test
    void northDakotaReusesTheEarlierTwo() {
        assertThat(otherJurisdictions(report(run(), "nd/2025/lower/HB0044"))).containsExactlyInAnyOrder("wy", "mt");
    }

    @Test
    void coloradoReusesNothing() {
        assertThat(report(run(), "co/2025/upper/SB0007").isEmpty()).isTrue();
    }

    @Test
    void theFirstInstrumentHasNothingToMatch() {
        assertThat(run().reports().getFirst().isEmpty()).isTrue();
    }

    @Test
    void thereAreTwoFamilies() {
        List<Lineage> families = families(run());
        assertThat(families).hasSize(2);
        List<Integer> sizes =
                new ArrayList<>(families.stream().map(Lineage::size).toList());
        sizes.sort(null);
        assertThat(sizes).containsExactly(2, 3);
    }

    @Test
    void theMunicipalFamilyIsSeparate() {
        Lineage municipal = families(run()).stream()
                .filter(family -> family.size() == 2)
                .findFirst()
                .orElseThrow();
        assertThat(municipal.members().stream().map(InstrumentKey::jurisdiction))
                .containsExactlyInAnyOrder("laramie", "cheyenne");
        assertThat(municipal.root()).isPresent();
        assertThat(municipal.root().get().jurisdiction()).isEqualTo("laramie");
    }

    @Test
    void theTwoFamiliesDoNotShareAMember() {
        List<Lineage> families = families(run());
        Set<String> first =
                families.get(0).members().stream().map(InstrumentKey::slug).collect(Collectors.toSet());
        Set<String> second = new HashSet<>(
                families.get(1).members().stream().map(InstrumentKey::slug).toList());
        second.retainAll(first);
        assertThat(second).isEmpty();
    }

    @Test
    void anOrdinanceMatchesOnlyTheOtherOrdinance() {
        assertThat(otherJurisdictions(report(run(), "cheyenne/2025/council/2025-08")))
                .containsExactly("laramie");
    }

    @Test
    void wyomingIsTheRoot() {
        Lineage family = stateFamily(run());
        assertThat(family.root()).isPresent();
        assertThat(family.root().get().jurisdiction()).isEqualTo("wy");
        assertThat(family.confident()).isTrue();
    }

    @Test
    void theFamilySpansThreeJurisdictionsAndTwoYears() {
        RunResult result = run();
        DiffusionMetrics metrics = DiffusionMetrics.measure(result.graph(), stateFamily(result));
        assertThat(metrics.jurisdictions()).isEqualTo(3);
        assertThat(metrics.spanDays()).hasValue(762);
        assertThat(metrics.characters()).isGreaterThan(500);
    }

    @Test
    void coloradoIsNotInTheFamily() {
        assertThat(stateFamily(run()).members())
                .allSatisfy(member -> assertThat(member.jurisdiction()).isNotEqualTo("co"));
    }

    @Test
    void theDroppedSubsectionIsNotReportedAsReused() {
        InstrumentReport report = report(run(), "nd/2025/lower/HB0044");
        String quoted = report.matches().stream()
                .flatMap(match -> match.passages().stream())
                .map(passage -> passage.queryText())
                .collect(Collectors.joining(" "));
        assertThat(quoted).doesNotContain("certified copy");
    }

    @Test
    void theRunIsReproducible() {
        RunResult first = run();
        RunResult second = run();
        assertThat(first.reports().stream()
                        .map(report -> report.summary().reusedCharacters())
                        .toList())
                .isEqualTo(second.reports().stream()
                        .map(report -> report.summary().reusedCharacters())
                        .toList());
        assertThat(first.graph().edges().stream().map(edge -> edge.pair()).toList())
                .isEqualTo(
                        second.graph().edges().stream().map(edge -> edge.pair()).toList());
    }
}

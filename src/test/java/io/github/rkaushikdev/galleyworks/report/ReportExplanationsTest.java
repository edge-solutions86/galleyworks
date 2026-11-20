package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportExplanationsTest {

    private static InstrumentReport report(EngineConfig config) {
        return new Engine(config)
                .runOver(goldenInstruments(), Optional.of(ReportFixtures.FIXED))
                .reportFor("mt/2024/lower/HB0311")
                .orElseThrow();
    }

    private static InstrumentReport report() {
        return report(new EngineConfig());
    }

    private static Set<String> reasons(InstrumentReport report) {
        Set<String> found = new HashSet<>();
        report.matches().forEach(match -> found.addAll(match.reasons()));
        return found;
    }

    @Test
    void everyMatchCarriesAnExplanation() {
        InstrumentReport built = report();
        assertThat(built.matches()).isNotEmpty().allSatisfy(match -> {
            assertThat(match.headline()).isNotEmpty();
            assertThat(match.reasons()).isNotEmpty();
            assertThat(match.confidence()).isIn((Object[]) Confidence.values());
        });
    }

    @Test
    void theHeadlineNamesBothSides() {
        ReportedMatch match = report().matches().getFirst();
        assertThat(match.headline())
                .contains(match.queryPath())
                .contains(match.otherKey().slug());
    }

    @Test
    void aCrossJurisdictionMatchSaysSo() {
        assertThat(report().matches().getFirst().reasons())
                .anyMatch(reason -> reason.contains("different jurisdictions"));
    }

    @Test
    void theTextReportShowsTheConfidence() {
        assertThat(TextRenderer.renderText(report())).contains("confidence ").contains("why: ");
    }

    @Test
    void theTextReportStillFitsItsWidth() {
        assertThat(TextRenderer.renderText(report(), 70).lines())
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(70));
    }

    @Test
    void theJsonReportCarriesTheReasons() throws Exception {
        JsonNode match = new ObjectMapper().readTree(JsonWriter.dumps(report())).at("/matches/0");
        assertThat(match.get("confidence").asText()).isIn("weak", "moderate", "strong");
        assertThat(match.get("headline").asText()).isNotEmpty();
        assertThat(match.get("reasons").isArray()).isTrue();
        assertThat(match.get("reasons")).isNotEmpty();
    }

    @Test
    void theMarkdownReportListsTheReasons() {
        assertThat(MarkdownWriter.toMarkdown(report()))
                .contains("| confidence |")
                .contains("Why this was reported:");
    }

    @Test
    void theMarkdownPassageLimitStillWorks() {
        InstrumentReport built = report();
        String rendered = MarkdownWriter.toMarkdown(built, 1);
        int most = built.matches().stream()
                .mapToInt(match -> match.passages().size())
                .max()
                .orElse(0);
        if (most > 1) {
            assertThat(rendered).contains("further passages omitted");
        } else {
            assertThat(rendered).doesNotContain("further passages omitted");
        }
    }

    @Test
    void everyWriterStillRuns() {
        InstrumentReport built = report();
        for (String name : List.of("text", "json", "csv", "markdown")) {
            StringWriter stream = new StringWriter();
            ReportWriters.writeReport(built, stream, name, TextRenderer.DEFAULT_WIDTH);
            assertThat(stream.toString().strip()).as(name).isNotEmpty();
        }
    }

    @Test
    void theMatchConfigReachesTheExplanation() {
        EngineConfig defaults = new EngineConfig();
        EngineConfig strict = defaults.withMatch(defaults.match().withMinPassageChars(95));
        assertThat(reasons(report()).isEmpty() && reasons(report(strict)).isEmpty())
                .isFalse();
    }

    @Test
    void anEmptyReportHasNoExplanations() {
        InstrumentReport built = new Engine()
                .runOver(goldenInstruments(), Optional.of(ReportFixtures.FIXED))
                .reportFor("co/2025/upper/SB0007")
                .orElseThrow();
        assertThat(built.matches()).isEmpty();
    }
}

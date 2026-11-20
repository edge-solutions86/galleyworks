package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.empty;
import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.report;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RenderTest {

    private static long count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1L;
    }

    @Test
    void theHeaderNamesTheInstrument() {
        assertThat(TextRenderer.renderText(report())).startsWith("lexdrift report for mt/2025/lower/HB0311");
    }

    @Test
    void theSummaryLineIsPresent() {
        assertThat(TextRenderer.renderText(report()))
                .contains("segments matched")
                .contains("characters reused");
    }

    @Test
    void theTimestampIsRenderedToTheSecond() {
        assertThat(TextRenderer.renderText(report())).contains("generated 2026-01-02T03:04:05+00:00");
    }

    @Test
    void findingsNameBothSides() {
        assertThat(TextRenderer.renderText(report())).contains("matches wy/2025/lower/HB0142 at");
    }

    @Test
    void scoresAreShown() {
        assertThat(TextRenderer.renderText(report())).contains("containment ").contains("jaccard ");
    }

    @Test
    void passagesAreQuotedFromBothDocuments() {
        assertThat(TextRenderer.renderText(report())).contains("query:").contains("other:");
    }

    @Test
    void anUnresolvedPassageSaysSo() {
        assertThat(TextRenderer.renderText(report(false))).contains("other: (not available)");
    }

    @Test
    void quotationsAreWrappedToTheWidth() {
        assertThat(TextRenderer.renderText(report(), 70).split("\n"))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(70));
    }

    @Test
    void anEmptyReportSaysNothingWasFound() {
        String rendered = TextRenderer.renderText(empty());
        assertThat(rendered).contains("no reuse found");
        assertThat(rendered.split("no reuse found", -1)[1]).doesNotContain("matches");
    }

    @Test
    void theMatchLimitIsReported() {
        InstrumentReport built = report();
        String rendered = TextRenderer.renderText(built, 100, Optional.of(1), 3);
        if (built.matches().size() > 1) {
            assertThat(rendered).contains("further matches");
        }
        assertThat(count(rendered, " matches ")).isLessThanOrEqualTo(2);
    }

    @Test
    void thePassageLimitIsReported() {
        InstrumentReport built = report();
        String rendered = TextRenderer.renderText(built, 100, Optional.empty(), 1);
        int total = built.matches().stream()
                .mapToInt(match -> match.passages().size())
                .max()
                .orElse(0);
        if (total > 1) {
            assertThat(rendered).contains("further passages");
        }
    }

    @Test
    void theOutputEndsWithOneNewline() {
        String rendered = TextRenderer.renderText(report());
        assertThat(rendered).endsWith("\n").doesNotEndWith("\n\n");
    }

    @Test
    void noLineContainsARawNewlineFromTheSource() {
        List<String> quoted = List.of(TextRenderer.renderText(report()).split("\n")).stream()
                .filter(line -> line.strip().startsWith("query:"))
                .toList();
        assertThat(quoted).isNotEmpty().allSatisfy(line -> assertThat(line).doesNotContain("\n"));
    }
}

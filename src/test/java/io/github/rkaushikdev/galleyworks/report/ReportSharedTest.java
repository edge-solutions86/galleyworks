package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.FIXED;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReportSharedTest {

    private static ReportedPassage passage(String text) {
        return new ReportedPassage(new TextSpan(0, 10), new TextSpan(0, 10), text, text, true);
    }

    private static ReportedMatch match(int count) {
        return new ReportedMatch(
                "1(a)",
                new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"),
                "1(a)",
                0.5,
                0.5,
                IntStream.range(0, count).mapToObj(n -> passage("passage " + n)).toList(),
                Confidence.MODERATE,
                "h",
                List.of("r"));
    }

    @Test
    void flowCollapsesEveryKindOfWhitespace() {
        assertThat(Shared.flow("  the\n board\tshall   act  ")).isEqualTo("the board shall act");
    }

    @Test
    void flowOfNothingIsEmpty() {
        assertThat(Shared.flow("   \n\t ")).isEmpty();
    }

    @Test
    void quoteReturnsTheFallbackForEmptyText() {
        assertThat(Shared.quote("", Optional.empty(), "(none)")).isEqualTo("(none)");
        assertThat(Shared.quote("   ", Optional.empty(), "(none)")).isEqualTo("(none)");
    }

    @Test
    void quoteLeavesShortTextAlone() {
        assertThat(Shared.quote("the board", Optional.of(100), "")).isEqualTo("the board");
    }

    @Test
    void quoteTruncatesWithAnEllipsis() {
        String truncated = Shared.quote("x".repeat(100), Optional.of(10), "");
        assertThat(truncated).hasSize(10).endsWith(Shared.ELLIPSIS);
    }

    @Test
    void quoteWithoutALimitNeverTruncates() {
        assertThat(Shared.quote("x".repeat(1000))).isEqualTo("x".repeat(1000));
    }

    @Test
    void spanPayloadIsSelfConsistent() {
        assertThat(Shared.spanPayload(new TextSpan(12, 40))).isEqualTo(Map.of("start", 12, "end", 40, "length", 28));
    }

    @Test
    void trimPassagesKeepsEverythingUnderTheLimit() {
        Shared.Trimmed trimmed = Shared.trimPassages(match(2), Optional.of(5));
        assertThat(trimmed.hidden()).isZero();
        assertThat(trimmed.match().passages()).hasSize(2);
    }

    @Test
    void trimPassagesReportsWhatItHid() {
        Shared.Trimmed trimmed = Shared.trimPassages(match(7), Optional.of(3));
        assertThat(trimmed.hidden()).isEqualTo(4);
        assertThat(trimmed.match().passages()).hasSize(3);
    }

    @Test
    void trimPassagesWithNoLimit() {
        Shared.Trimmed trimmed = Shared.trimPassages(match(7), Optional.empty());
        assertThat(trimmed.hidden()).isZero();
        assertThat(trimmed.match().passages()).hasSize(7);
    }

    @Test
    void bothWritersQuoteAPassageIdentically() {
        InstrumentReport report = new Engine()
                .runOver(GoldenCorpus.goldenInstruments(), Optional.of(FIXED))
                .reportFor("mt/2024/lower/HB0311")
                .orElseThrow();
        String expected =
                Shared.flow(report.matches().getFirst().passages().getFirst().queryText());
        assertThat(MarkdownWriter.toMarkdown(report)).contains(expected);
        assertThat(TextRenderer.renderText(report, 10_000))
                .contains(expected.substring(0, Math.min(60, expected.length())));
    }
}

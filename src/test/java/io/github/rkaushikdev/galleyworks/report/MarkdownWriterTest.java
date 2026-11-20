package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.empty;
import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.report;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class MarkdownWriterTest {

    private static long count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1L;
    }

    @Test
    void theHeadingNamesTheInstrument() {
        assertThat(MarkdownWriter.toMarkdown(report())).startsWith("# Reuse report: mt/2025/lower/HB0311");
    }

    @Test
    void theTimestampIsPresent() {
        assertThat(MarkdownWriter.toMarkdown(report())).contains("Generated 2026-01-02T03:04:05+00:00.");
    }

    @Test
    void theSummaryIsATable() {
        assertThat(MarkdownWriter.toMarkdown(report()))
                .contains("## Summary")
                .contains("| segments matched |")
                .contains("| reuse ratio |");
    }

    @Test
    void findingsAreNumbered() {
        assertThat(MarkdownWriter.toMarkdown(report())).contains("## Findings").contains("### 1. ");
    }

    @Test
    void aFindingNamesBothSides() {
        assertThat(MarkdownWriter.toMarkdown(report())).contains("into wy/2025/lower/HB0142");
    }

    @Test
    void passagesAreFenced() {
        String rendered = MarkdownWriter.toMarkdown(report());
        assertThat(count(rendered, "```") % 2).isZero();
        assertThat(count(rendered, "```")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void quotationsAreOneLineInsideTheFence() {
        assertThat(MarkdownWriter.toMarkdown(report()).split("```", -1)[1].strip())
                .doesNotContain("\n");
    }

    @Test
    void thePassageLimitIsReported() {
        InstrumentReport built = report();
        String rendered = MarkdownWriter.toMarkdown(built, 1);
        if (built.matches().stream()
                        .mapToInt(match -> match.passages().size())
                        .max()
                        .orElse(0)
                > 1) {
            assertThat(rendered).contains("further passages omitted");
        }
    }

    @Test
    void pipesInACellAreEscaped() {
        for (String line : MarkdownWriter.toMarkdown(report()).split("\n")) {
            if (line.startsWith("| other jurisdiction |")) {
                assertThat(count(line, "|")).isEqualTo(3);
            }
        }
    }

    @Test
    void anEmptyReportSaysSo() {
        assertThat(MarkdownWriter.toMarkdown(empty()))
                .contains("No reuse found.")
                .doesNotContain("## Findings");
    }

    @Test
    void theDocumentEndsWithOneNewline() {
        assertThat(MarkdownWriter.toMarkdown(report())).endsWith("\n").doesNotEndWith("\n\n");
    }

    @Test
    void writeMarkdownSendsItToAStream() {
        StringWriter stream = new StringWriter();
        MarkdownWriter.writeMarkdown(report(), stream);
        assertThat(stream.toString()).isEqualTo(MarkdownWriter.toMarkdown(report()));
    }
}

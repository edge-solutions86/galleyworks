package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.report;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ConfigError;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportWritersTest {

    private static String rendered(String format) {
        StringWriter stream = new StringWriter();
        ReportWriters.writeReport(report(), stream, format, TextRenderer.DEFAULT_WIDTH);
        return stream.toString();
    }

    @Test
    void everyFormatIsRegistered() {
        assertThat(ReportWriters.formatNames()).containsExactly("text", "json", "csv", "markdown");
    }

    @Test
    void everyWriterDescribesItself() {
        assertThat(ReportWriters.FORMATS.values()).allSatisfy(writer -> {
            assertThat(writer.description()).isNotEmpty();
            assertThat(writer.suffix()).isNotEmpty();
        });
    }

    @Test
    void suffixesAreDistinct() {
        List<String> suffixes = ReportWriters.FORMATS.values().stream()
                .map(ReportWriters.ReportWriter::suffix)
                .toList();
        assertThat(new HashSet<>(suffixes)).hasSameSizeAs(suffixes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json", "csv", "markdown"})
    void everyFormatWritesSomething(String name) {
        assertThat(rendered(name).strip()).isNotEmpty();
    }

    @Test
    void textIsTheDefault() {
        StringWriter stream = new StringWriter();
        ReportWriters.writeReport(report(), stream);
        assertThat(stream.toString()).startsWith("lexdrift report for");
    }

    @Test
    void jsonIsJson() {
        assertThat(rendered("json").stripLeading()).startsWith("{");
    }

    @Test
    void csvStartsWithItsHeader() {
        assertThat(rendered("csv")).startsWith("query_instrument,");
    }

    @Test
    void markdownStartsWithAHeading() {
        assertThat(rendered("markdown")).startsWith("# Reuse report:");
    }

    @Test
    void theWidthReachesTheTextWriter() {
        StringWriter stream = new StringWriter();
        ReportWriters.writeReport(report(), stream, "text", 60);
        assertThat(stream.toString().split("\n"))
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(60));
    }

    @Test
    void suffixLookup() {
        assertThat(ReportWriters.suffixFor("markdown")).isEqualTo("md");
        assertThat(ReportWriters.suffixFor("text")).isEqualTo("txt");
    }

    @Test
    void anUnknownFormatNamesTheKnownOnes() {
        assertThatThrownBy(() -> ReportWriters.writeReport(report(), new StringWriter(), "yaml", 100))
                .isInstanceOfSatisfying(ConfigError.class, caught -> {
                    assertThat(caught.getMessage()).contains("unknown output format");
                    assertThat((String) caught.context().get("known")).contains("markdown");
                });
    }

    @Test
    void anUnknownSuffixIsRejected() {
        assertThatThrownBy(() -> ReportWriters.suffixFor("yaml"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unknown output format");
    }
}

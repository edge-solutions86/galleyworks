package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.Engine;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The output formats are a contract, so they are pinned.
 *
 * <p>Every one of these has a consumer that breaks silently when the shape moves: a spreadsheet
 * with a column order, a script reading a JSON key, a ticket template expecting a heading. Renaming
 * a field is allowed; doing it without noticing is not. They assert structure and stable keys
 * rather than whole documents: a golden file of the entire rendered report would fail on every
 * wording change and be updated without being read.
 */
class FormatStabilityTest {

    private static final Set<String> TOP_LEVEL_KEYS =
            Set.of("format", "instrument", "generated_at", "summary", "matches");
    private static final Set<String> SUMMARY_KEYS = Set.of(
            "matched_segments",
            "total_segments",
            "reused_characters",
            "document_characters",
            "other_instruments",
            "reuse_ratio");
    private static final Set<String> MATCH_KEYS = Set.of(
            "query_path",
            "other",
            "containment",
            "jaccard",
            "characters",
            "resolved",
            "confidence",
            "headline",
            "reasons",
            "passages");
    private static final Set<String> PASSAGE_KEYS =
            Set.of("query", "other", "characters", "resolved", "query_text", "other_text");

    private static InstrumentReport report;

    @BeforeAll
    static void report() {
        report = new Engine()
                .runOver(goldenInstruments(), Optional.of(ReportFixtures.FIXED))
                .reportFor("nd/2025/lower/HB0044")
                .orElseThrow();
        assertThat(report.matches()).isNotEmpty();
    }

    private static String rendered(String name) {
        StringWriter stream = new StringWriter();
        ReportWriters.writeReport(report, stream, name, TextRenderer.DEFAULT_WIDTH);
        return stream.toString();
    }

    @SuppressWarnings("unchecked") // the JSON mapping nests plain maps and lists
    private static Map<String, Object> firstMatch() {
        return ((List<Map<String, Object>>) JsonWriter.toMapping(report).get("matches")).getFirst();
    }

    @Test
    void theJsonFormatVersionIsPinned() {
        assertThat(JsonWriter.toMapping(report).get("format")).isEqualTo(JsonWriter.FORMAT_VERSION);
        assertThat(JsonWriter.FORMAT_VERSION).isEqualTo(1);
    }

    @Test
    void theJsonTopLevelKeysArePinned() {
        assertThat(JsonWriter.toMapping(report).keySet()).isEqualTo(TOP_LEVEL_KEYS);
    }

    @Test
    void theJsonSummaryKeysArePinned() {
        assertThat(((Map<?, ?>) JsonWriter.toMapping(report).get("summary")).keySet())
                .isEqualTo(SUMMARY_KEYS);
    }

    @Test
    void theJsonMatchKeysArePinned() {
        assertThat(firstMatch().keySet()).isEqualTo(MATCH_KEYS);
    }

    @Test
    void theJsonPassageKeysArePinned() {
        assertThat(((Map<?, ?>) ((List<?>) firstMatch().get("passages")).getFirst()).keySet())
                .isEqualTo(PASSAGE_KEYS);
    }

    @Test
    void theJsonInstrumentBlockIsPinned() {
        assertThat(((Map<?, ?>) JsonWriter.toMapping(report).get("instrument")).keySet())
                .isEqualTo(Set.of("key", "jurisdiction", "session", "chamber", "number", "title"));
    }

    @Test
    void theCsvColumnOrderIsPinned() {
        assertThat(CsvWriter.COLUMNS.subList(0, 4))
                .containsExactly("query_instrument", "query_jurisdiction", "query_path", "other_instrument");
        assertThat(CsvWriter.COLUMNS.getLast()).isEqualTo("query_text");
        assertThat(CsvWriter.COLUMNS).hasSize(15);
    }

    @Test
    void theCsvHeaderMatchesTheColumns() {
        String header = rendered("csv").lines().findFirst().orElseThrow();
        assertThat(List.of(header.split(",", -1))).isEqualTo(CsvWriter.COLUMNS);
    }

    @Test
    void theTextReportOpensWithItsHeading() {
        assertThat(rendered("text").lines().findFirst().orElseThrow()).startsWith("lexdrift report for ");
    }

    @Test
    void theMarkdownReportOpensWithItsHeading() {
        assertThat(rendered("markdown").lines().findFirst().orElseThrow()).startsWith("# Reuse report: ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json", "csv", "markdown"})
    void everyFormatEndsWithASingleNewline(String name) {
        String output = rendered(name);
        assertThat(output).endsWith("\n").doesNotEndWith("\n\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "json", "csv", "markdown"})
    void everyFormatIsDeterministic(String name) {
        assertThat(rendered(name)).isEqualTo(rendered(name));
    }

    @Test
    void theFormatListIsPinned() {
        assertThat(ReportWriters.formatNames()).containsExactly("text", "json", "csv", "markdown");
    }

    @Test
    void theJsonDocumentParsesAndRoundTrips() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Object parsed = mapper.readValue(rendered("json"), Object.class);
        Object expected = mapper.readValue(mapper.writeValueAsString(JsonWriter.toMapping(report)), Object.class);
        assertThat(parsed).isEqualTo(expected);
    }
}

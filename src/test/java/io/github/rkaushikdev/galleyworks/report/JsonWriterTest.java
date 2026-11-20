package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.FIXED;
import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.empty;
import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.report;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonWriterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked") // the writer builds every nested object as a Map<String, Object>
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked") // the writer builds every nested array as a List<Object>
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> firstPassage(Map<String, Object> payload) {
        return map(list(map(list(payload.get("matches")).getFirst()).get("passages"))
                .getFirst());
    }

    @Test
    void thePayloadDeclaresItsFormat() {
        assertThat(JsonWriter.toMapping(report())).containsEntry("format", JsonWriter.FORMAT_VERSION);
    }

    @Test
    void theInstrumentBlockIsSplitOut() {
        Map<String, Object> instrument = map(JsonWriter.toMapping(report()).get("instrument"));
        assertThat(instrument)
                .containsEntry("key", "mt/2025/lower/HB0311")
                .containsEntry("jurisdiction", "mt")
                .containsEntry("chamber", "lower");
        assertThat((String) instrument.get("title")).startsWith("AN ACT");
    }

    @Test
    void theTimestampIsIso() {
        assertThat(JsonWriter.toMapping(report())).containsEntry("generated_at", "2026-01-02T03:04:05+00:00");
    }

    @Test
    void theSummaryIsPresentAndRounded() {
        Map<String, Object> summary = map(JsonWriter.toMapping(report()).get("summary"));
        assertThat((Integer) summary.get("matched_segments")).isGreaterThanOrEqualTo(1);
        assertThat((Double) summary.get("reuse_ratio")).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(summary.get("reused_characters")).isInstanceOf(Integer.class);
    }

    @Test
    void spansCarryTheirLength() {
        Map<String, Object> span =
                map(firstPassage(JsonWriter.toMapping(report())).get("query"));
        assertThat(span.get("length")).isEqualTo((Integer) span.get("end") - (Integer) span.get("start"));
    }

    @Test
    void textIsIncludedByDefault() {
        Map<String, Object> passage = firstPassage(JsonWriter.toMapping(report()));
        assertThat((String) passage.get("query_text")).isNotEmpty();
        assertThat((String) passage.get("other_text")).isNotEmpty();
    }

    @Test
    void textCanBeLeftOut() {
        Map<String, Object> passage = firstPassage(JsonWriter.toMapping(report(), false));
        assertThat(passage).doesNotContainKey("query_text");
        assertThat((Integer) passage.get("characters")).isPositive();
    }

    @Test
    void theOtherInstrumentIsFullyNamed() {
        Map<String, Object> other =
                map(map(list(JsonWriter.toMapping(report()).get("matches")).getFirst())
                        .get("other"));
        assertThat(other)
                .containsEntry("instrument", "wy/2025/lower/HB0142")
                .containsEntry("jurisdiction", "wy")
                .containsEntry("number", "HB0142");
    }

    @Test
    void dumpsProducesParseableJson() throws Exception {
        JsonNode payload = JSON.readTree(JsonWriter.dumps(report()));
        assertThat(payload.get("instrument").get("key").asText()).isEqualTo("mt/2025/lower/HB0311");
    }

    @Test
    void dumpsCanBeCompact() {
        assertThat(JsonWriter.dumps(report(), false, true).strip()).doesNotContain("\n");
    }

    @Test
    void writeJsonTerminatesWithANewline() throws Exception {
        StringWriter stream = new StringWriter();
        JsonWriter.writeJson(report(), stream);
        String written = stream.toString();
        assertThat(written).endsWith("\n");
        assertThat(JSON.readTree(written).get("format").asInt()).isEqualTo(JsonWriter.FORMAT_VERSION);
    }

    @Test
    void anEmptyReportStillSerializes() throws Exception {
        JsonNode payload = JSON.readTree(JsonWriter.dumps(empty()));
        assertThat(payload.get("matches")).isEmpty();
        assertThat(payload.get("summary").get("reuse_ratio").asDouble()).isEqualTo(0.0);
    }

    @Test
    void nonAsciiSurvives() throws Exception {
        var probe = makeBill("pr", "PC0001", "El consejo publicará el padrón.");
        JsonNode payload =
                JSON.readTree(JsonWriter.dumps(Reports.buildReport(probe, List.of(), new MappingTextSource(), FIXED)));
        assertThat(payload.get("instrument").get("jurisdiction").asText()).isEqualTo("pr");
    }
}

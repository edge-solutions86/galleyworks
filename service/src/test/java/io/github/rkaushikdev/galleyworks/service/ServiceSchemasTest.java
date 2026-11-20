package io.github.rkaushikdev.galleyworks.service;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.MappingTextSource;
import io.github.rkaushikdev.galleyworks.report.Reports;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceSchemasTest {

    private static final OffsetDateTime FIXED = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);

    private static InstrumentReport report() {
        Instrument sourceBill = makeBill("wy", "HB0142", MODEL_CLAUSE);
        Instrument probe = makeBill("mt", "HB0311", MODEL_CLAUSE);
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(sourceBill));
        return Reports.buildReport(
                probe, new Matcher(index).matchInstrument(probe), new MappingTextSource(List.of(sourceBill)), FIXED);
    }

    @Test
    void aQueryNeedsTextAndAJurisdiction() {
        Schemas.QueryRequest request = Schemas.QueryRequest.of(Map.of("text", "AN ACT", "jurisdiction", "wy"));
        assertThat(request.session()).isNull();
        assertThat(request.includeText()).isTrue();
    }

    @Test
    void anEmptyQueryIsRejected() {
        assertThatThrownBy(() -> new Schemas.QueryRequest("", "wy"))
                .isInstanceOf(RequestRejected.class)
                .satisfies(exc -> assertThat(((RequestRejected) exc).problems().getFirst())
                        .containsEntry("type", "string_too_short")
                        .containsEntry("loc", List.of("text")));
    }

    @Test
    void unknownFieldsAreRejected() {
        assertThatThrownBy(() -> Schemas.QueryRequest.of(Map.of("text", "x", "jurisdiction", "wy", "extra", 1)))
                .isInstanceOf(RequestRejected.class)
                .satisfies(exc -> assertThat(((RequestRejected) exc).problems().getFirst())
                        .containsEntry("type", "extra_forbidden")
                        .containsEntry("loc", List.of("body", "extra")));
    }

    @Test
    void requestsAreFrozen() {
        // A record has no setters and its fields are final, so a request cannot be changed after
        // it has been validated.
        assertThat(Schemas.QueryRequest.class.isRecord()).isTrue();
        assertThat(Arrays.stream(Schemas.QueryRequest.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .isNotEmpty()
                .allSatisfy(field ->
                        assertThat(Modifier.isFinal(field.getModifiers())).isTrue());
    }

    @Test
    void aReportMapsOntoTheWireModel() {
        Schemas.ReportOut payload = Schemas.ReportOut.of(report(), "abc123");
        assertThat(payload.instrument()).isEqualTo("mt/2025/lower/HB0311");
        assertThat(payload.jurisdiction()).isEqualTo("mt");
        assertThat(payload.engineDigest()).isEqualTo("abc123");
        assertThat(payload.generatedAt()).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(payload.matches()).isNotEmpty();
    }

    @Test
    void theSummaryIsCarriedAcross() {
        Schemas.SummaryOut summary = Schemas.ReportOut.of(report(), "abc123").summary();
        assertThat(summary.matchedSegments()).isGreaterThanOrEqualTo(1);
        assertThat(summary.reuseRatio()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void matchesNameTheOtherInstrument() {
        Schemas.MatchOut match =
                Schemas.ReportOut.of(report(), "abc123").matches().getFirst();
        assertThat(match.otherInstrument()).isEqualTo("wy/2025/lower/HB0142");
        assertThat(match.otherJurisdiction()).isEqualTo("wy");
        assertThat(match.characters()).isPositive();
    }

    @Test
    void passagesCarrySpansAndText() {
        Schemas.PassageOut passage = Schemas.ReportOut.of(report(), "abc123")
                .matches()
                .getFirst()
                .passages()
                .getFirst();
        assertThat(passage.queryEnd()).isGreaterThan(passage.queryStart());
        assertThat(passage.queryText()).isNotEmpty();
        assertThat(passage.resolved()).isTrue();
    }

    @Test
    void textCanBeLeftOut() {
        Schemas.PassageOut passage = Schemas.ReportOut.of(report(), "abc123", false)
                .matches()
                .getFirst()
                .passages()
                .getFirst();
        assertThat(passage.queryText()).isNull();
        assertThat(passage.characters()).isPositive();
    }

    @Test
    void scoresAreRounded() {
        double containment =
                Schemas.ReportOut.of(report(), "abc123").matches().getFirst().containment();
        String text = Double.toString(containment);
        assertThat(text.substring(text.indexOf('.') + 1)).hasSizeLessThanOrEqualTo(6);
    }

    @Test
    void anEmptyReportMapsCleanly() {
        Instrument probe = makeBill("co", "SB0007", LOCAL_CLAUSE);
        Schemas.ReportOut payload =
                Schemas.ReportOut.of(Reports.buildReport(probe, List.of(), new MappingTextSource(), FIXED), "abc123");
        assertThat(payload.matches()).isEmpty();
        assertThat(payload.summary().reuseRatio()).isZero();
    }

    private static List<Map<String, Object>> problems(Object body) {
        try {
            Schemas.QueryRequest.of(body);
        } catch (RequestRejected exc) {
            return exc.problems();
        }
        throw new AssertionError("accepted " + body);
    }

    @Test
    void everyProblemWithABodyIsNamed() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", 5);
        body.put("session", "x".repeat(33));
        body.put("include_text", "maybe");
        body.put("colour", "red");
        assertThat(problems(body))
                .extracting(problem -> problem.get("type") + " " + problem.get("loc"))
                .containsExactly(
                        "string_type [body, text]",
                        "missing [body, jurisdiction]",
                        "string_too_long [body, session]",
                        "bool_parsing [body, include_text]",
                        "extra_forbidden [body, colour]");
        assertThat(problems(body).get(2))
                .containsEntry("msg", "String should have at most 32 characters")
                .containsEntry("ctx", Map.of("max_length", 32));
        assertThat(problems(body).get(1)).containsEntry("msg", "Field required").containsEntry("input", body);
    }

    @Test
    void aBodyThatIsNotAnObjectIsRefused() {
        assertThat(problems(List.of(1, 2)).getFirst())
                .containsEntry("type", "model_attributes_type")
                .containsEntry("loc", List.of("body"));
    }

    @Test
    void includeTextReadsTheUsualSpellingsOfYesAndNo() {
        for (Object yes : List.of(true, 1, 1.0, "true", " Yes ", "on", "t", "y", "1")) {
            assertThat(Schemas.QueryRequest.of(Map.of("text", "x", "jurisdiction", "wy", "include_text", yes))
                            .includeText())
                    .as(String.valueOf(yes))
                    .isTrue();
        }
        for (Object no : List.of(false, 0, 0.0, "false", "NO", "off", "f", "n", "0")) {
            assertThat(Schemas.QueryRequest.of(Map.of("text", "x", "jurisdiction", "wy", "include_text", no))
                            .includeText())
                    .as(String.valueOf(no))
                    .isFalse();
        }
        assertThat(problems(Map.of("text", "x", "jurisdiction", "wy", "include_text", 2))
                        .getFirst())
                .containsEntry("type", "bool_parsing");
        assertThat(problems(Map.of("text", "x", "jurisdiction", "wy", "include_text", List.of()))
                        .getFirst())
                .containsEntry("type", "bool_type");
    }

    @Test
    void optionalFieldsMayBeNull() {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("text", "x", "jurisdiction", "wy"));
        body.put("session", null);
        body.put("format_name", null);
        assertThat(Schemas.QueryRequest.of(body).formatName()).isNull();
        body.put("jurisdiction", null);
        assertThat(problems(body).getFirst()).containsEntry("type", "string_type");
    }

    @Test
    void timestampsAreWrittenInUtcWithMicroseconds() {
        assertThat(Schemas.timestamp(FIXED)).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(Schemas.timestamp(FIXED.withNano(123_456_789))).isEqualTo("2026-01-02T03:04:05.123456Z");
        assertThat(Schemas.timestamp(FIXED.withOffsetSameInstant(ZoneOffset.ofHours(2))))
                .isEqualTo("2026-01-02T05:04:05+02:00");
    }

    @Test
    void theHealthModelRoundTrips() throws Exception {
        Schemas.HealthResponse health = new Schemas.HealthResponse("ok", "0.1.0", "abc123", 2, 9);
        Map<?, ?> written = new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(health), Map.class);
        assertThat(written.get("status")).isEqualTo("ok");
        assertThat(written.get("engine_digest")).isEqualTo("abc123");
        assertThat(health.segments()).isEqualTo(9);
    }
}

package io.github.rkaushikdev.galleyworks.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.ReportSummary;
import io.github.rkaushikdev.galleyworks.report.ReportedMatch;
import io.github.rkaushikdev.galleyworks.report.ReportedPassage;
import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The request and response bodies, field for field as they appear on the wire. Every name is
 * written in snake case, and a request may carry no field the schema does not name.
 */
public final class Schemas {

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final Set<String> TRUE = Set.of("1", "on", "t", "true", "y", "yes");
    private static final Set<String> FALSE = Set.of("0", "off", "f", "false", "n", "no");

    private Schemas() {}

    /**
     * {@code when} as the wire writes a timestamp: to the second, then microseconds when there are
     * any, then {@code Z} for UTC or the offset.
     */
    public static String timestamp(OffsetDateTime when) {
        int micros = when.getNano() / 1000;
        String fraction = micros == 0 ? "" : String.format(Locale.ROOT, ".%06d", micros);
        String offset =
                when.getOffset().equals(ZoneOffset.UTC) ? "Z" : when.getOffset().getId();
        return SECONDS.format(when) + fraction + offset;
    }

    /**
     * Ask what one document reuses.
     *
     * @param text the document, as printed
     * @param jurisdiction the jurisdiction's slug
     * @param session the session, when the text does not say
     * @param number the printed number, when the text omits it
     * @param formatName the reader to use instead of detecting one
     * @param includeText whether passages quote the text on both sides
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @Schema(name = "QueryRequest", description = "Ask what one document reuses.")
    public record QueryRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = TEXT_LIMIT) String text,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = FIELD_LIMIT)
                    String jurisdiction,
            @Schema(nullable = true, maxLength = FIELD_LIMIT) String session,
            @Schema(nullable = true, maxLength = FIELD_LIMIT) String number,
            @Schema(nullable = true, maxLength = FIELD_LIMIT) String formatName,
            @Schema(defaultValue = "true") boolean includeText) {

        /** The longest document a query may send, in characters. */
        public static final int TEXT_LIMIT = 4_000_000;

        /** The longest jurisdiction, session, number or reader name, in characters. */
        public static final int FIELD_LIMIT = 32;

        private static final List<String> FIELDS =
                List.of("text", "jurisdiction", "session", "number", "format_name", "include_text");

        /** Validates every field. */
        public QueryRequest {
            Map<String, Object> given = new LinkedHashMap<>();
            given.put("text", text);
            given.put("jurisdiction", jurisdiction);
            given.put("session", session);
            given.put("number", number);
            given.put("format_name", formatName);
            given.put("include_text", includeText);
            check(given, List.of());
        }

        /** A request for {@code text} from {@code jurisdiction}, every other field at its default. */
        public QueryRequest(String text, String jurisdiction) {
            this(text, jurisdiction, null, null, null, true);
        }

        /**
         * Read a request from a decoded JSON body.
         *
         * @throws RequestRejected naming every problem with the body
         */
        public static QueryRequest of(Object body) {
            return of(body, List.of("body"));
        }

        static QueryRequest of(Object body, List<Object> at) {
            if (!(body instanceof Map<?, ?> fields)) {
                throw RequestRejected.one(
                        "model_attributes_type",
                        at,
                        "Input should be a valid dictionary or object to extract fields from",
                        body);
            }
            Map<String, Object> given = new LinkedHashMap<>();
            fields.forEach((key, value) -> given.put(String.valueOf(key), value));
            check(given, at);
            Object include = given.getOrDefault("include_text", Boolean.TRUE);
            return new QueryRequest(
                    (String) given.get("text"),
                    (String) given.get("jurisdiction"),
                    (String) given.get("session"),
                    (String) given.get("number"),
                    (String) given.get("format_name"),
                    flag(include).orElseThrow());
        }

        private static void check(Map<String, Object> given, List<Object> at) {
            RequestRejected.Problems problems = new RequestRejected.Problems();
            text(problems, given, at, "text", true, 1, TEXT_LIMIT);
            text(problems, given, at, "jurisdiction", true, 1, FIELD_LIMIT);
            text(problems, given, at, "session", false, 0, FIELD_LIMIT);
            text(problems, given, at, "number", false, 0, FIELD_LIMIT);
            text(problems, given, at, "format_name", false, 0, FIELD_LIMIT);
            if (given.containsKey("include_text")) {
                Object value = given.get("include_text");
                if (flag(value).isEmpty()) {
                    boolean parsable = value instanceof String || value instanceof Number;
                    problems.add(
                            parsable ? "bool_parsing" : "bool_type",
                            where(at, "include_text"),
                            parsable
                                    ? "Input should be a valid boolean, unable to interpret input"
                                    : "Input should be a valid boolean",
                            value);
                }
            }
            for (Map.Entry<String, Object> entry : given.entrySet()) {
                if (!FIELDS.contains(entry.getKey())) {
                    problems.add(
                            "extra_forbidden",
                            where(at, entry.getKey()),
                            "Extra inputs are not permitted",
                            entry.getValue());
                }
            }
            problems.raiseIfAny();
        }

        private static void text(
                RequestRejected.Problems problems,
                Map<String, Object> given,
                List<Object> at,
                String field,
                boolean required,
                int minimum,
                int maximum) {
            if (!given.containsKey(field)) {
                if (required) {
                    problems.add("missing", where(at, field), "Field required", given);
                }
                return;
            }
            Object value = given.get(field);
            if (value == null && !required) {
                return;
            }
            if (!(value instanceof String string)) {
                problems.add("string_type", where(at, field), "Input should be a valid string", value);
                return;
            }
            int length = string.codePointCount(0, string.length());
            if (length < minimum) {
                problems.add(
                        "string_too_short",
                        where(at, field),
                        "String should have at least " + minimum + " character" + (minimum == 1 ? "" : "s"),
                        value,
                        Map.of("min_length", minimum));
            } else if (length > maximum) {
                problems.add(
                        "string_too_long",
                        where(at, field),
                        "String should have at most " + maximum + " character" + (maximum == 1 ? "" : "s"),
                        value,
                        Map.of("max_length", maximum));
            }
        }

        private static Optional<Boolean> flag(Object value) {
            if (value instanceof Boolean flag) {
                return Optional.of(flag);
            }
            if (value instanceof Number number && !(value instanceof Double || value instanceof Float)) {
                long whole = number.longValue();
                return whole == 0 || whole == 1 ? Optional.of(whole == 1) : Optional.empty();
            }
            if (value instanceof Number number) {
                double real = number.doubleValue();
                return real == 0.0 || real == 1.0 ? Optional.of(real == 1.0) : Optional.empty();
            }
            if (value instanceof String text) {
                String lowered = Text.lower(Text.strip(text));
                if (TRUE.contains(lowered)) {
                    return Optional.of(true);
                }
                if (FALSE.contains(lowered)) {
                    return Optional.of(false);
                }
            }
            return Optional.empty();
        }

        private static List<Object> where(List<Object> at, String field) {
            List<Object> loc = new ArrayList<>(at);
            loc.add(field);
            return loc;
        }
    }

    /**
     * One reused passage.
     *
     * @param queryStart where it starts in the queried document
     * @param queryEnd where it ends in the queried document
     * @param otherStart where it starts in the other instrument
     * @param otherEnd where it ends in the other instrument
     * @param characters its length
     * @param resolved whether the other instrument's text was available
     * @param queryText the queried document's words, unless text was left out
     * @param otherText the other instrument's words, unless text was left out
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PassageOut(
            int queryStart,
            int queryEnd,
            int otherStart,
            int otherEnd,
            int characters,
            boolean resolved,
            @Schema(nullable = true) String queryText,
            @Schema(nullable = true) String otherText) {

        /** {@code passage} on the wire. */
        public static PassageOut of(ReportedPassage passage, boolean includeText) {
            return new PassageOut(
                    passage.querySpan().start(),
                    passage.querySpan().end(),
                    passage.otherSpan().start(),
                    passage.otherSpan().end(),
                    passage.characters(),
                    passage.resolved(),
                    includeText ? passage.queryText() : null,
                    includeText ? passage.otherText() : null);
        }
    }

    /**
     * One finding.
     *
     * @param queryPath the queried document's segment
     * @param otherInstrument the instrument it matches
     * @param otherJurisdiction that instrument's jurisdiction
     * @param otherPath that instrument's segment
     * @param containment how much of the segment appears in the other, to six places
     * @param jaccard the overlap of the two segments, to six places
     * @param characters reused characters across the passages
     * @param confidence {@code weak}, {@code moderate} or {@code strong}
     * @param headline one sentence naming both sides
     * @param reasons why it was reported
     * @param passages the reused passages
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record MatchOut(
            String queryPath,
            String otherInstrument,
            String otherJurisdiction,
            String otherPath,
            double containment,
            double jaccard,
            int characters,
            @Schema(allowableValues = {"weak", "moderate", "strong"}) String confidence,
            String headline,
            List<String> reasons,
            List<PassageOut> passages) {

        /** Copies the lists. */
        public MatchOut {
            reasons = List.copyOf(reasons);
            passages = List.copyOf(passages);
        }

        /** {@code match} on the wire. */
        public static MatchOut of(ReportedMatch match, boolean includeText) {
            return new MatchOut(
                    match.queryPath(),
                    match.otherKey().slug(),
                    match.otherKey().jurisdiction(),
                    match.otherPath(),
                    Floats.round(match.containment(), 6),
                    Floats.round(match.jaccard(), 6),
                    match.characters(),
                    match.confidence().value(),
                    match.headline(),
                    match.reasons(),
                    match.passages().stream()
                            .map(passage -> PassageOut.of(passage, includeText))
                            .toList());
        }
    }

    /**
     * How much of a document was reused.
     *
     * @param matchedSegments segments with at least one finding
     * @param totalSegments segments in the document
     * @param reusedCharacters characters covered by a reused passage
     * @param documentCharacters characters in the document
     * @param otherInstruments distinct instruments it reuses
     * @param reuseRatio the reused share, to six places
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SummaryOut(
            int matchedSegments,
            int totalSegments,
            int reusedCharacters,
            int documentCharacters,
            int otherInstruments,
            double reuseRatio) {

        /** {@code summary} on the wire. */
        public static SummaryOut of(ReportSummary summary) {
            return new SummaryOut(
                    summary.matchedSegments(),
                    summary.totalSegments(),
                    summary.reusedCharacters(),
                    summary.documentCharacters(),
                    summary.otherInstruments(),
                    Floats.round(summary.reuseRatio(), 6));
        }
    }

    /**
     * What a query answers.
     *
     * @param instrument the queried document's key
     * @param jurisdiction its jurisdiction
     * @param title its title
     * @param generatedAt when the report was built
     * @param engineDigest the digest of the engine configuration that answered
     * @param summary how much was reused
     * @param matches the findings, strongest first
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReportOut(
            String instrument,
            String jurisdiction,
            String title,
            @Schema(format = "date-time") String generatedAt,
            String engineDigest,
            SummaryOut summary,
            List<MatchOut> matches) {

        /** Copies the findings. */
        public ReportOut {
            matches = List.copyOf(matches);
        }

        /** {@code report} on the wire, quoting the text. */
        public static ReportOut of(InstrumentReport report, String digest) {
            return of(report, digest, true);
        }

        /** {@code report} on the wire. */
        public static ReportOut of(InstrumentReport report, String digest, boolean includeText) {
            return new ReportOut(
                    report.key().slug(),
                    report.key().jurisdiction(),
                    report.title(),
                    timestamp(report.generatedAt()),
                    digest,
                    SummaryOut.of(report.summary()),
                    report.matches().stream()
                            .map(match -> MatchOut.of(match, includeText))
                            .toList());
        }
    }

    /**
     * One indexed instrument.
     *
     * @param key its slug
     * @param jurisdiction its jurisdiction
     * @param session its session
     * @param chamber its chamber of origin
     * @param number its printed number
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InstrumentOut(String key, String jurisdiction, String session, String chamber, String number) {

        /** {@code key} on the wire. */
        public static InstrumentOut of(InstrumentKey key) {
            return new InstrumentOut(
                    key.slug(), key.jurisdiction(), key.session(), key.chamber().value(), key.number());
        }
    }

    /**
     * One page of the corpus listing.
     *
     * @param total instruments matching the filter
     * @param offset where the page starts
     * @param limit the most the page may hold
     * @param instruments the page
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InstrumentsResponse(int total, long offset, int limit, List<InstrumentOut> instruments) {

        /** Copies the page. */
        public InstrumentsResponse {
            instruments = List.copyOf(instruments);
        }

        /** True when a later page has more instruments. */
        public boolean hasMore() {
            return offset + instruments.size() < total;
        }
    }

    /**
     * One indexed segment of an instrument.
     *
     * @param index its position in the instrument
     * @param path its printed label chain
     * @param tokens its canonical tokens
     * @param fingerprints the fingerprints kept for it
     * @param contentRatio the share of its tokens that are not function words, to four places
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SegmentOut(int index, String path, int tokens, int fingerprints, double contentRatio) {}

    /**
     * Everything the index holds about one instrument. The segment list makes a finding checkable
     * from outside: a client told that {@code 1(a)(ii)} matches can ask which segments exist and how
     * much text each carries without holding the corpus.
     *
     * @param instrument the instrument
     * @param segments its segments in document order
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InstrumentDetail(InstrumentOut instrument, List<SegmentOut> segments) {

        /** Copies the segments. */
        public InstrumentDetail {
            segments = List.copyOf(segments);
        }

        /** Tokens across every segment. */
        public int totalTokens() {
            return segments.stream().mapToInt(SegmentOut::tokens).sum();
        }
    }

    /**
     * What {@code GET /health} answers.
     *
     * @param status {@code ok}
     * @param version the release version
     * @param engineDigest the digest of the engine configuration
     * @param instruments indexed instruments
     * @param segments indexed segments
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record HealthResponse(String status, String version, String engineDigest, int instruments, int segments) {}
}

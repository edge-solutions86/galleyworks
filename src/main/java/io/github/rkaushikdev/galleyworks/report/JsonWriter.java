package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Json;
import io.github.rkaushikdev.galleyworks.util.Timestamps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON output: versioned, and flat enough to query with {@code jq}.
 *
 * <p>Spans are objects rather than two-element arrays, because a report is read by people as often
 * as by programs. Passage text is included by default; it is the bulk of the payload, so it can be
 * left out when the consumer already holds both documents and only wants the offsets.
 */
public final class JsonWriter {

    /** The version of the JSON layout. */
    public static final int FORMAT_VERSION = 1;

    private static final int ROUNDING = 6;

    private JsonWriter() {}

    private static Map<String, Object> passage(ReportedPassage passage, boolean includeText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", Shared.spanPayload(passage.querySpan()));
        payload.put("other", Shared.spanPayload(passage.otherSpan()));
        payload.put("characters", passage.characters());
        payload.put("resolved", passage.resolved());
        if (includeText) {
            payload.put("query_text", passage.queryText());
            payload.put("other_text", passage.otherText());
        }
        return payload;
    }

    private static Map<String, Object> match(ReportedMatch match, boolean includeText) {
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("instrument", match.otherKey().slug());
        other.put("jurisdiction", match.otherKey().jurisdiction());
        other.put("session", match.otherKey().session());
        other.put("number", match.otherKey().number());
        other.put("path", match.otherPath());
        List<Object> passages = new ArrayList<>();
        match.passages().forEach(item -> passages.add(passage(item, includeText)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query_path", match.queryPath());
        payload.put("other", other);
        payload.put("containment", Floats.round(match.containment(), ROUNDING));
        payload.put("jaccard", Floats.round(match.jaccard(), ROUNDING));
        payload.put("characters", match.characters());
        payload.put("resolved", match.resolved());
        payload.put("confidence", match.confidence().value());
        payload.put("headline", match.headline());
        payload.put("reasons", match.reasons());
        payload.put("passages", passages);
        return payload;
    }

    /** The report as plain JSON-ready data, passage text included. */
    public static Map<String, Object> toMapping(InstrumentReport report) {
        return toMapping(report, true);
    }

    /** The report as plain JSON-ready data. */
    public static Map<String, Object> toMapping(InstrumentReport report, boolean includeText) {
        ReportSummary summary = report.summary();
        Map<String, Object> instrument = new LinkedHashMap<>();
        instrument.put("key", report.key().slug());
        instrument.put("jurisdiction", report.key().jurisdiction());
        instrument.put("session", report.key().session());
        instrument.put("chamber", report.key().chamber().value());
        instrument.put("number", report.key().number());
        instrument.put("title", report.title());
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("matched_segments", summary.matchedSegments());
        totals.put("total_segments", summary.totalSegments());
        totals.put("reused_characters", summary.reusedCharacters());
        totals.put("document_characters", summary.documentCharacters());
        totals.put("other_instruments", summary.otherInstruments());
        totals.put("reuse_ratio", Floats.round(summary.reuseRatio(), ROUNDING));
        List<Object> matches = new ArrayList<>();
        report.matches().forEach(item -> matches.add(match(item, includeText)));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", FORMAT_VERSION);
        payload.put("instrument", instrument);
        payload.put("generated_at", Timestamps.isoformat(report.generatedAt()));
        payload.put("summary", totals);
        payload.put("matches", matches);
        return payload;
    }

    /** The report as indented JSON, passage text included. */
    public static String dumps(InstrumentReport report) {
        return dumps(report, true, true);
    }

    /** The report as JSON, two-space indented or on one line, non-ASCII written as it is. */
    public static String dumps(InstrumentReport report, boolean indented, boolean includeText) {
        Json.Style style = (indented ? Json.Style.indented() : Json.Style.compact()).unicode();
        return Json.write(toMapping(report, includeText), style);
    }

    /** Write the report as indented JSON, newline terminated. */
    public static void writeJson(InstrumentReport report, Writer stream) {
        writeJson(report, stream, true, true);
    }

    /** Write the report as JSON, newline terminated. */
    public static void writeJson(InstrumentReport report, Writer stream, boolean indented, boolean includeText) {
        try {
            stream.write(dumps(report, indented, includeText));
            stream.write("\n");
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }
}

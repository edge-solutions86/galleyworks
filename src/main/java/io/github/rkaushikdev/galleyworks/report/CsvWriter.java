package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.Floats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CSV output, one row per passage.
 *
 * <p>The row is the passage rather than the match because that is the unit an analyst sorts and
 * filters on. Quotations are collapsed to a single line: a newline inside a CSV field is legal, but
 * a legislative passage is four lines of wrapped text and a row that spans four screen lines makes
 * the file unreadable. Fields are quoted only when they hold a comma, a quote or a line break.
 */
public final class CsvWriter {

    /** The columns, in order. */
    public static final List<String> COLUMNS = List.of(
            "query_instrument",
            "query_jurisdiction",
            "query_path",
            "other_instrument",
            "other_jurisdiction",
            "other_path",
            "containment",
            "jaccard",
            "characters",
            "query_start",
            "query_end",
            "other_start",
            "other_end",
            "resolved",
            "query_text");

    private static final int MAX_QUOTE = 400;

    private CsvWriter() {}

    /** One row per passage, keyed by {@link #COLUMNS}. */
    public static List<Map<String, Object>> flatten(InstrumentReport report) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ReportedMatch match : report.matches()) {
            for (ReportedPassage passage : match.passages()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("query_instrument", report.key().slug());
                row.put("query_jurisdiction", report.key().jurisdiction());
                row.put("query_path", match.queryPath());
                row.put("other_instrument", match.otherKey().slug());
                row.put("other_jurisdiction", match.otherKey().jurisdiction());
                row.put("other_path", match.otherPath());
                row.put("containment", Floats.fixed(match.containment(), 6));
                row.put("jaccard", Floats.fixed(match.jaccard(), 6));
                row.put("characters", passage.characters());
                row.put("query_start", passage.querySpan().start());
                row.put("query_end", passage.querySpan().end());
                row.put("other_start", passage.otherSpan().start());
                row.put("other_end", passage.otherSpan().end());
                row.put("resolved", passage.resolved() ? "true" : "false");
                row.put("query_text", Shared.quote(passage.queryText(), Optional.of(MAX_QUOTE), ""));
                rows.add(row);
            }
        }
        return rows;
    }

    /** Write the report as CSV with a header, returning the number of rows written. */
    public static int writeCsv(InstrumentReport report, Writer stream) {
        return writeCsv(report, stream, true);
    }

    /** Write the report as CSV, returning the number of rows written. */
    public static int writeCsv(InstrumentReport report, Writer stream, boolean header) {
        List<Map<String, Object>> rows = flatten(report);
        try {
            if (header) {
                stream.write(line(COLUMNS));
            }
            for (Map<String, Object> row : rows) {
                stream.write(line(COLUMNS.stream()
                        .map(column -> String.valueOf(row.get(column)))
                        .toList()));
            }
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
        return rows.size();
    }

    private static String line(List<String> fields) {
        List<String> cells = new ArrayList<>();
        for (String field : fields) {
            cells.add(cell(field));
        }
        return String.join(",", cells) + "\n";
    }

    private static String cell(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}

package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The report format registry. Every command takes its formats, file suffixes and descriptions from
 * this one table, so a format is added in one place and every command grows it at once.
 */
public final class ReportWriters {

    /** Writes one report to a stream at a line width. */
    @FunctionalInterface
    public interface Emit {

        /** Write {@code report} to {@code stream}. */
        void emit(InstrumentReport report, Writer stream, int width) throws IOException;
    }

    /**
     * One output format.
     *
     * @param name the name commands accept
     * @param suffix the file suffix it writes
     * @param emit what writes it
     * @param description a short description for help text
     */
    public record ReportWriter(String name, String suffix, Emit emit, String description) {}

    /** Every format, by name, in a stable order. */
    public static final Map<String, ReportWriter> FORMATS = formats();

    private ReportWriters() {}

    private static Map<String, ReportWriter> formats() {
        Map<String, ReportWriter> formats = new LinkedHashMap<>();
        for (ReportWriter writer : List.of(
                new ReportWriter(
                        "text",
                        "txt",
                        (report, stream, width) -> stream.write(TextRenderer.renderText(report, width)),
                        "plain text for a terminal"),
                new ReportWriter(
                        "json",
                        "json",
                        (report, stream, width) -> JsonWriter.writeJson(report, stream),
                        "versioned json for a program"),
                new ReportWriter(
                        "csv",
                        "csv",
                        (report, stream, width) -> CsvWriter.writeCsv(report, stream),
                        "one row per passage"),
                new ReportWriter(
                        "markdown",
                        "md",
                        (report, stream, width) -> MarkdownWriter.writeMarkdown(report, stream),
                        "markdown for a ticket"))) {
            formats.put(writer.name(), writer);
        }
        return Collections.unmodifiableMap(formats);
    }

    /** Every format name, in a stable order. */
    public static List<String> formatNames() {
        return new ArrayList<>(FORMATS.keySet());
    }

    private static ReportWriter writer(String name) {
        ReportWriter found = FORMATS.get(name);
        if (found == null) {
            throw new ConfigError(
                    "unknown output format",
                    Context.of("output_format", name, "known", String.join(",", formatNames())));
        }
        return found;
    }

    /** The file suffix a format writes. */
    public static String suffixFor(String name) {
        return writer(name).suffix();
    }

    /** Write {@code report} as text at the default width. */
    public static void writeReport(InstrumentReport report, Writer stream) {
        writeReport(report, stream, "text", TextRenderer.DEFAULT_WIDTH);
    }

    /** Write {@code report} in the named format. */
    public static void writeReport(InstrumentReport report, Writer stream, String outputFormat, int width) {
        ReportWriter chosen = writer(outputFormat);
        try {
            chosen.emit().emit(report, stream, width);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }
}

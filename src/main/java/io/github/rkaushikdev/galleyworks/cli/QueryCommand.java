package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.ReportWriters;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift query}: ask what an instrument reuses.
 *
 * <p>The document being asked about is never added to the index: a bill under consideration is
 * checked against what is already enacted, and adding it would make it match itself on the next
 * run. The exit code is a finding, so {@code --fail-over 0.4} works as a gate in a drafting
 * pipeline.
 */
@Command(
        name = "query",
        header = "ask what an instrument reuses from an index",
        customSynopsis = "lexdrift query [OPTIONS] DOCUMENT",
        description = "Check DOCUMENT against an index without adding it.")
public final class QueryCommand extends Common.Base {

    @Parameters(paramLabel = "DOCUMENT")
    String document;

    @Mixin
    Common.RequiredIndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Mixin
    Common.DisplayOptions display;

    @Mixin
    Hints hints;

    @Option(
            names = "--output",
            paramLabel = "[text|json|csv|markdown]",
            defaultValue = "text",
            converter = Choices.Formats.class,
            completionCandidates = Choices.Formats.class,
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "how to print the report")
    String outputFormat;

    @Option(
            names = "--width",
            paramLabel = "INTEGER",
            defaultValue = "100",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "text wrap width")
    int width;

    @Option(
            names = "--fail-over",
            paramLabel = "FLOAT",
            converter = Choices.FloatValue.class,
            description = "exit 1 when the reuse ratio is at or above this value")
    Double failOver;

    @Override
    public Integer call() {
        Engine engine = engineOptions.engine(Optional.of(index.path()));
        try {
            Instrument instrument = engine.registry().read(hints.read(document));
            engine.texts().add(instrument);
            InstrumentReport report = engine.analyse(instrument).report();
            ReportWriters.writeReport(display.narrow(report), out(), outputFormat, width);
            out().flush();
            if (failOver != null && report.summary().reuseRatio() >= failOver) {
                return Main.EXIT_FINDINGS;
            }
            return Main.EXIT_OK;
        } finally {
            Common.close(engine.index());
        }
    }

    /** {@code --jurisdiction}, {@code --session} and {@code --number}, for a document read alone. */
    public static final class Hints {
        @Option(
                names = "--jurisdiction",
                paramLabel = "TEXT",
                required = true,
                description = "jurisdiction slug for the document")
        String jurisdiction;

        @Option(names = "--session", paramLabel = "TEXT", description = "session, when the text does not say")
        String session;

        @Option(names = "--number", paramLabel = "TEXT", description = "instrument number, when the text omits it")
        String number;

        /** The document at {@code path}, read with these hints. */
        public SourceDocument read(String path) {
            return Hints.read(path, jurisdiction, session, number);
        }

        /** The document at {@code path}, read with the hints that were given. */
        static SourceDocument read(String path, String jurisdiction, String session, String number) {
            Map<String, Object> hints = new LinkedHashMap<>();
            hints.put("jurisdiction", jurisdiction);
            if (session != null) {
                hints.put("session", session);
            }
            if (number != null) {
                hints.put("number", number);
            }
            return SourceDocument.fromPath(Path.of(path), hints);
        }
    }
}

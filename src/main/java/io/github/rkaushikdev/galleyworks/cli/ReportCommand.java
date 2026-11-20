package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.ReportWriters;
import io.github.rkaushikdev.galleyworks.util.Regex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift report}: run a whole corpus and write one report per instrument.
 *
 * <p>This is the batch counterpart of {@code query}: it walks a corpus in order, indexing as it
 * goes, so each instrument is checked against everything that came before it. That ordering is what
 * makes the output readable as a history rather than as a pile of symmetric pairs.
 */
@Command(
        name = "report",
        header = "run a corpus and write one report per instrument",
        customSynopsis = "lexdrift report [OPTIONS] SOURCE",
        description = "Run the corpus at SOURCE and report every instrument.")
public final class ReportCommand extends Common.Base {

    private static final Pattern UNSAFE = Regex.compile("[^A-Za-z0-9._-]+");

    @Parameters(paramLabel = "SOURCE")
    String source;

    @Mixin
    Common.IndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Mixin
    Common.DisplayOptions display;

    @Option(
            names = "--out",
            paramLabel = "DIRECTORY",
            converter = Choices.DirectoryPath.class,
            description = "write one file per instrument into this directory")
    Path outDir;

    @Option(
            names = "--output",
            paramLabel = "[text|json|csv|markdown]",
            defaultValue = "text",
            converter = Choices.Formats.class,
            completionCandidates = Choices.Formats.class,
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "report format")
    String outputFormat;

    @Option(names = "--only-findings", description = "skip instruments with no reuse")
    boolean onlyFindings;

    @Option(
            names = "--width",
            paramLabel = "INTEGER",
            defaultValue = "100",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "text wrap width")
    int width;

    /** A file name that survives every filesystem we care about. */
    public static String fileNameFor(InstrumentKey key, String outputFormat) {
        String stem = stripDashes(UNSAFE.matcher(key.slug()).replaceAll("-"));
        return stem + "." + ReportWriters.suffixFor(outputFormat);
    }

    @Override
    public Integer call() {
        Engine engine = engineOptions.engine(index.path());
        try {
            return run(engine);
        } finally {
            Common.close(engine.index());
        }
    }

    private int run(Engine engine) {
        RunResult result = engine.run(IngestCommand.loadCorpus(source));
        if (outDir != null) {
            try {
                Files.createDirectories(outDir);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }
        int written = 0;
        int findings = 0;
        for (InstrumentReport raw : result.reports()) {
            InstrumentReport built = display.narrow(raw);
            if (built.isEmpty()) {
                if (onlyFindings) {
                    continue;
                }
            } else {
                findings++;
            }
            if (outDir == null) {
                ReportWriters.writeReport(built, out(), outputFormat, width);
                out().flush();
            } else {
                Path path = outDir.resolve(fileNameFor(built.key(), outputFormat));
                try (Writer stream = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    ReportWriters.writeReport(built, stream, outputFormat, width);
                } catch (IOException exc) {
                    throw new UncheckedIOException(exc);
                }
                written++;
            }
        }
        if (outDir != null) {
            line(written + " reports written to " + outDir);
        }
        line(result.instrumentCount() + " instruments, " + findings + " with reuse, "
                + result.graph().size() + " edges");
        return findings > 0 ? Main.EXIT_FINDINGS : Main.EXIT_OK;
    }

    private static String stripDashes(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }
}

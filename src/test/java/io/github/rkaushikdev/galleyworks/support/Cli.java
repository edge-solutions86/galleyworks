package io.github.rkaushikdev.galleyworks.support;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.cli.Main;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Runs the command line in process and lays out corpora for it. */
public final class Cli {

    private Cli() {}

    /**
     * What one run produced.
     *
     * @param code the exit code
     * @param output what the command wrote to its output stream
     * @param errors what it wrote to its error stream
     */
    public record Result(int code, String output, String errors) {}

    /** Run {@code argv} with no completion request in the environment. */
    public static Result run(String... argv) {
        return run(Map.of(), argv);
    }

    /** Run {@code argv} against {@code environ}. */
    public static Result run(Map<String, String> environ, String... argv) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        int code = Main.run(List.of(argv), out, err, environ);
        return new Result(code, out.toString(), err.toString());
    }

    /** Write {@code text} to {@code path} as UTF-8. */
    public static Path write(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
        return path;
    }

    /** Read {@code path} as UTF-8. */
    public static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    /** Create {@code path} and its parents. */
    public static Path mkdir(Path path) {
        try {
            return Files.createDirectories(path);
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }

    /** Write {@code instrument} under {@code root} as {@code <jurisdiction>_<session>_<number>.txt}. */
    public static Path writeBill(Path root, Instrument instrument) {
        InstrumentKey key = instrument.key();
        return write(
                root.resolve(key.jurisdiction() + "_" + key.session() + "_" + key.number() + ".txt"),
                instrument.rawText());
    }

    /** Write {@code instrument} under {@code root} as {@code <jurisdiction>_2025_<number>.txt}. */
    public static Path writeBill2025(Path root, Instrument instrument) {
        InstrumentKey key = instrument.key();
        return write(root.resolve(key.jurisdiction() + "_2025_" + key.number() + ".txt"), instrument.rawText());
    }

    /** A {@code bills} directory under {@code tmp} holding the golden corpus. */
    public static Path goldenCorpus(Path tmp) {
        Path root = mkdir(tmp.resolve("bills"));
        GoldenCorpus.goldenInstruments().forEach(instrument -> writeBill(root, instrument));
        return root;
    }

    /** The number of times {@code needle} occurs in {@code text}. */
    public static int count(String text, String needle) {
        int found = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            found++;
            at = text.indexOf(needle, at + needle.length());
        }
        return found;
    }
}

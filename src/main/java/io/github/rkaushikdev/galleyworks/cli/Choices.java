package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.report.ReportWriters;
import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Repr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * The value types the options accept: fixed choices, paths that must not be the wrong kind of
 * thing, and floats with underscores and the infinities. Every refusal is a usage error.
 */
public final class Choices {

    private Choices() {}

    /** A value that must be one of a fixed list, which also serves as its completion candidates. */
    public abstract static class Choice implements ITypeConverter<String>, Iterable<String> {

        /** The accepted values, in the order they are offered. */
        public abstract List<String> choices();

        @Override
        public String convert(String value) {
            List<String> choices = choices();
            if (choices.contains(value)) {
                return value;
            }
            String listed = choices.stream().map(Repr::quote).collect(Collectors.joining(", "));
            String expected = choices.size() == 1 ? listed : "one of " + listed;
            throw new TypeConversionException(Repr.quote(value) + " is not " + expected + ".");
        }

        @Override
        public Iterator<String> iterator() {
            return choices().iterator();
        }
    }

    /** {@code --log-level}. */
    public static final class LogLevels extends Choice {
        @Override
        public List<String> choices() {
            return List.of("debug", "info", "warning", "error");
        }
    }

    /** {@code --log-format}. */
    public static final class LogFormats extends Choice {
        @Override
        public List<String> choices() {
            return List.of("plain", "json");
        }
    }

    /** {@code --reading}. */
    public static final class Readings extends Choice {
        @Override
        public List<String> choices() {
            return List.of("current", "proposed");
        }
    }

    /** {@code --min-confidence}. */
    public static final class Confidences extends Choice {
        @Override
        public List<String> choices() {
            return Arrays.stream(Confidence.values()).map(Confidence::value).toList();
        }
    }

    /** {@code --output}. */
    public static final class Formats extends Choice {
        @Override
        public List<String> choices() {
            return ReportWriters.formatNames();
        }
    }

    /** The shells a completion script exists for. */
    public static final class Shells extends Choice {
        @Override
        public List<String> choices() {
            return Completion.SHELLS;
        }
    }

    /** A path that may be missing but must not be a directory. */
    public static final class FilePath implements ITypeConverter<Path> {
        @Override
        public Path convert(String value) {
            Path path = Path.of(value);
            if (Files.isDirectory(path)) {
                throw new TypeConversionException("File " + quoted(value) + " is a directory.");
            }
            return path;
        }
    }

    /** A path that may be missing but must not be a file. */
    public static final class DirectoryPath implements ITypeConverter<Path> {
        @Override
        public Path convert(String value) {
            Path path = Path.of(value);
            if (Files.isRegularFile(path)) {
                throw new TypeConversionException("Directory " + quoted(value) + " is a file.");
            }
            return path;
        }
    }

    /** A float, where {@code inf} and {@code 1_000} are accepted. */
    public static final class FloatValue implements ITypeConverter<Double> {
        @Override
        public Double convert(String value) {
            try {
                return Floats.parse(value);
            } catch (NumberFormatException exc) {
                throw new TypeConversionException(Repr.quote(value) + " is not a valid float.");
            }
        }
    }

    private static String quoted(String value) {
        return "'" + value + "'";
    }
}

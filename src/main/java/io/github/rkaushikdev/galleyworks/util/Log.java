package io.github.rkaushikdev.galleyworks.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Structured logging for the engine: SLF4J loggers under the {@code lexdrift} namespace, an
 * ambient context, and one stderr handler the CLI installs.
 *
 * <p>Corpus runs are long and mostly unattended, so the log is the only record of what the engine
 * decided. Events carry key-value fields ({@code log.atInfo().addKeyValue(...)}) that the JSON
 * layout flattens, which keeps the log greppable without inventing a logging framework.
 */
public final class Log {

    /** The namespace every engine logger lives under. */
    public static final String ROOT = "lexdrift";

    private static final Map<String, Level> LEVELS = Map.of(
            "CRITICAL", Level.ERROR,
            "FATAL", Level.ERROR,
            "ERROR", Level.ERROR,
            "WARN", Level.WARN,
            "WARNING", Level.WARN,
            "INFO", Level.INFO,
            "DEBUG", Level.DEBUG,
            "NOTSET", Level.TRACE);

    private Log() {}

    /** A logger for {@code name}, placed under the {@code lexdrift} namespace. */
    public static org.slf4j.Logger get(String name) {
        if (name.startsWith(ROOT)) {
            return LoggerFactory.getLogger(name);
        }
        return LoggerFactory.getLogger(ROOT + "." + name);
    }

    /** Install a single stderr handler writing JSON lines at {@code level}. */
    public static void configure(String level) {
        configure(level, true);
    }

    /** Install a single stderr handler, replacing any earlier one. */
    public static void configure(String level, boolean jsonOutput) {
        configure(level, jsonOutput, System.err);
    }

    /**
     * Install a single handler on {@code stream}, replacing any earlier one.
     *
     * <p>The stream is never closed by the logging system, so reconfiguring on {@code System.err}
     * cannot close stderr for the process.
     */
    public static void configure(String level, boolean jsonOutput, OutputStream stream) {
        Level numeric = LEVELS.get(level.toUpperCase(Locale.ROOT));
        if (numeric == null) {
            throw new IllegalArgumentException("unknown log level " + Repr.quote(level));
        }
        Logger root = packageLogger();
        LoggerContext context = root.getLoggerContext();
        root.detachAndStopAllAppenders();

        Layout<ILoggingEvent> layout = jsonOutput ? new JsonLayout() : new PlainLayout();
        layout.setContext(context);
        layout.start();
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(context);
        encoder.setLayout(layout);
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setName("lexdrift");
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setImmediateFlush(true);
        appender.setOutputStream(new KeepOpen(stream));
        appender.start();

        root.addAppender(appender);
        root.setLevel(numeric);
        root.setAdditive(false);
    }

    /** Attach {@code fields} to every event logged while {@code block} runs. */
    public static void bound(Map<String, ?> fields, Runnable block) {
        bound(fields, () -> {
            block.run();
            return null;
        });
    }

    /** Attach {@code fields} to every event logged while {@code block} runs, returning its value. */
    public static <T> T bound(Map<String, ?> fields, Supplier<T> block) {
        Map<String, String> saved = MDC.getCopyOfContextMap();
        Map<String, String> merged = saved == null ? new HashMap<>() : new HashMap<>(saved);
        fields.forEach((key, value) -> merged.put(key, String.valueOf(value)));
        MDC.setContextMap(merged);
        try {
            return block.get();
        } finally {
            if (saved == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(saved);
            }
        }
    }

    static Logger packageLogger() {
        return (Logger) LoggerFactory.getLogger(ROOT);
    }

    /** The level name the log lines print for a Logback level. */
    static String levelName(Level level) {
        return switch (level.toInt()) {
            case Level.WARN_INT -> "WARNING";
            case Level.TRACE_INT -> "DEBUG";
            default -> level.toString();
        };
    }

    /** Flushes on close instead of closing, so a shared stream outlives its appender. */
    private static final class KeepOpen extends FilterOutputStream {

        KeepOpen(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}

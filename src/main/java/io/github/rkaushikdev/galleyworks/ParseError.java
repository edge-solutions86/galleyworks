package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.Map;

/** A document parser hit text it cannot account for, at a known line and column. */
public class ParseError extends IngestError {

    private final int line;
    private final int column;

    /** A parse failure at column zero of {@code line}. */
    public ParseError(String message, int line) {
        this(message, line, 0, Map.of());
    }

    /** A parse failure at {@code line} and {@code column}. */
    public ParseError(String message, int line, int column) {
        this(message, line, column, Map.of());
    }

    /** A parse failure at {@code line} and {@code column}, with further context. */
    public ParseError(String message, int line, int column, Map<String, ?> context) {
        super(message, located(line, column, context));
        this.line = line;
        this.column = column;
    }

    private static Map<String, Object> located(int line, int column, Map<String, ?> context) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("line", line);
        merged.put("column", column);
        merged.putAll(context);
        return merged;
    }

    @Override
    public String code() {
        return "lexdrift.ingest.parse";
    }

    /** The 1-based line the parser stopped on. */
    public int line() {
        return line;
    }

    /** The column the parser stopped on. */
    public int column() {
        return column;
    }

    /** {@code line N, column M: message}. */
    public String located() {
        return "line " + line + ", column " + column + ": " + message();
    }
}

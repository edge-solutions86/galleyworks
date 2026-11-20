package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.IngestError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raw document text plus whatever the caller already knows about it: the jurisdiction, session,
 * number, chamber or title it was filed under.
 *
 * @param text the document as printed
 * @param origin where it came from, for messages
 * @param hints identity and metadata the caller supplies, by name
 */
public record SourceDocument(String text, String origin, Map<String, Object> hints) {

    private static final int SNIFF_LINES = 40;

    /** Copies the hints. */
    public SourceDocument {
        hints = Collections.unmodifiableMap(new LinkedHashMap<>(hints));
    }

    /** A document held in memory with no hints. */
    public SourceDocument(String text) {
        this(text, "<memory>", Map.of());
    }

    /** A document held in memory with {@code hints}. */
    public SourceDocument(String text, Map<String, Object> hints) {
        this(text, "<memory>", hints);
    }

    /** Read {@code path} as UTF-8, with {@code hints}. */
    public static SourceDocument fromPath(Path path, Map<String, Object> hints) {
        try {
            return new SourceDocument(Files.readString(path, StandardCharsets.UTF_8), path.toString(), hints);
        } catch (IOException exc) {
            throw new IngestError("source document cannot be read", Context.of("path", path.toString()), exc);
        }
    }

    /** Read {@code path} as UTF-8, with no hints. */
    public static SourceDocument fromPath(Path path) {
        return fromPath(path, Map.of());
    }

    /** The opening lines a sniffer should look at, cheaply. */
    public List<String> firstLines() {
        String[] parts = text.split("\n", SNIFF_LINES + 1);
        return List.of(Arrays.copyOf(parts, Math.min(parts.length, SNIFF_LINES)));
    }

    /** The hint {@code name}, when the caller supplied a non-empty one. */
    public Optional<Object> hint(String name) {
        Object value = hints.get(name);
        if (value == null || (value instanceof String text && text.isEmpty())) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /** The same document with different text: the hints and origin are kept. */
    public SourceDocument withText(String replacement) {
        return new SourceDocument(replacement, origin, hints);
    }
}

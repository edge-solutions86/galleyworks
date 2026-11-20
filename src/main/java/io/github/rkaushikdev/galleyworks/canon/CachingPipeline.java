package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.fingerprint.Hashing;
import io.github.rkaushikdev.galleyworks.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A byte-bounded least-recently-used cache in front of a canonicalization pipeline.
 *
 * <p>Statutes repeat themselves: a corpus of amendments to one chapter re-prints the same section
 * in every bill that touches it, and canonicalizing it again for each one is the largest avoidable
 * cost in a corpus run. The cache is keyed on the text and the settings together; keying on text
 * alone would return a result canonicalized under a different profile. Eviction is driven by the
 * characters held rather than by entry count, because the keys are whole documents.
 */
public final class CachingPipeline implements Canonicalizer {

    /** The default bound on characters held. */
    public static final int DEFAULT_LIMIT = 8 * 1024 * 1024;

    private static final Json.Style KEY_STYLE = Json.Style.compact().separators(",", ":");

    private final CanonPipeline pipeline;
    private final long limit;
    private final Map<String, CanonResult> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;
    private int hits;
    private int misses;

    /**
     * How the cache is behaving.
     *
     * @param hits lookups answered from the cache
     * @param misses lookups that ran the pipeline
     * @param entries results held
     * @param bytesHeld characters held across every result
     */
    public record CacheStats(int hits, int misses, int entries, long bytesHeld) {

        /** Every lookup so far. */
        public int lookups() {
            return hits + misses;
        }

        /** The share of lookups answered from the cache. */
        public double hitRate() {
            return lookups() == 0 ? 0.0 : (double) hits / lookups();
        }
    }

    /** A cache with the default bound. */
    public CachingPipeline(CanonPipeline pipeline) {
        this(pipeline, DEFAULT_LIMIT);
    }

    /** A cache holding at most {@code limitBytes} characters of results. */
    public CachingPipeline(CanonPipeline pipeline, long limitBytes) {
        if (limitBytes < 0) {
            throw new ConfigError("cache limit cannot be negative", Context.of("limit_bytes", limitBytes));
        }
        this.pipeline = pipeline;
        this.limit = limitBytes;
    }

    @Override
    public List<String> ruleNames() {
        return pipeline.ruleNames();
    }

    private static String key(String text, CanonConfig config) {
        byte[] settings = Json.write(config.toMapping(), KEY_STYLE).getBytes(StandardCharsets.UTF_8);
        byte[] blob = text.getBytes(StandardCharsets.UTF_8);
        return Hashing.hex(Hashing.hashBytes(settings)) + ":" + blob.length + ":"
                + Hashing.hex(Hashing.hashBytes(blob));
    }

    /** Canonicalize {@code text}, reusing an earlier result when there is one. */
    @Override
    public synchronized CanonResult run(String text, CanonConfig config) {
        String key = key(text, config);
        CanonResult found = entries.get(key);
        if (found != null) {
            hits++;
            return found;
        }
        misses++;
        CanonResult result = pipeline.run(text, config);
        store(key, result);
        return result;
    }

    private void store(String key, CanonResult result) {
        long size = sizeOf(result);
        if (size > limit) {
            return;
        }
        entries.put(key, result);
        bytes += size;
        Iterator<CanonResult> eldest = entries.values().iterator();
        while (bytes > limit && eldest.hasNext()) {
            bytes -= sizeOf(eldest.next());
            eldest.remove();
        }
    }

    private static long sizeOf(CanonResult result) {
        return (long) result.source().length() + result.text().length();
    }

    /** How the cache is behaving now. */
    public synchronized CacheStats stats() {
        return new CacheStats(hits, misses, entries.size(), bytes);
    }

    /** Drop every held result; the counters are kept. */
    public synchronized void clear() {
        entries.clear();
        bytes = 0;
    }
}

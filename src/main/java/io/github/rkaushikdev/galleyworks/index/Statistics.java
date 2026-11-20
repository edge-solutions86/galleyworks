package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.util.Floats;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Describing what is in an index.
 *
 * <p>Two questions an operator asks before trusting a result: is the corpus what I think it is, and
 * is the index healthy for querying. The second is the useful one: a bucket holding half the corpus
 * is not a bug in the data, it is a signal that the banding is too loose for this corpus and every
 * query is paying for it.
 */
public final class Statistics {

    private Statistics() {}

    /**
     * A measured description of an index.
     *
     * @param instruments distinct instruments
     * @param segments indexed segments
     * @param jurisdictions instruments per jurisdiction, in the order first seen
     * @param sessions instruments per session, in the order first seen
     * @param totalTokens tokens across every segment
     * @param totalFingerprints selected fingerprints across every segment
     * @param distinctFingerprints distinct fingerprint values
     * @param widestPosting segments carrying the most widely shared fingerprint
     * @param digest the engine digest
     */
    public record CorpusStats(
            int instruments,
            int segments,
            Map<String, Integer> jurisdictions,
            Map<String, Integer> sessions,
            long totalTokens,
            long totalFingerprints,
            int distinctFingerprints,
            int widestPosting,
            String digest) {

        /** Copies the counts, keeping their order. */
        public CorpusStats {
            jurisdictions = Collections.unmodifiableMap(new LinkedHashMap<>(jurisdictions));
            sessions = Collections.unmodifiableMap(new LinkedHashMap<>(sessions));
        }

        /** An empty description of {@code instruments} and {@code segments}. */
        public CorpusStats(int instruments, int segments) {
            this(instruments, segments, Map.of(), Map.of(), 0, 0, 0, 0, "");
        }

        /** Tokens per segment. */
        public double meanTokens() {
            return segments == 0 ? 0.0 : (double) totalTokens / segments;
        }

        /** Fingerprints per segment. */
        public double meanFingerprints() {
            return segments == 0 ? 0.0 : (double) totalFingerprints / segments;
        }

        /** Distinct fingerprints over total: one means nothing is repeated. */
        public double selectivity() {
            return totalFingerprints == 0 ? 0.0 : (double) distinctFingerprints / totalFingerprints;
        }

        /** The share of segments the widest posting covers. */
        public double widestPostingShare() {
            return segments == 0 ? 0.0 : (double) widestPosting / segments;
        }

        /** A short human-readable summary. */
        public List<String> lines() {
            String counted = new TreeMap<>(jurisdictions)
                    .entrySet().stream()
                            .map(entry -> entry.getKey() + " " + entry.getValue())
                            .collect(Collectors.joining(", "));
            String years = String.join(", ", new TreeSet<>(sessions.keySet()));
            return List.of(
                    instruments + " instruments, " + segments + " segments",
                    "jurisdictions: " + (counted.isEmpty() ? "none" : counted),
                    "sessions: " + (years.isEmpty() ? "none" : years),
                    "mean " + Floats.fixed(meanTokens(), 1) + " tokens and " + Floats.fixed(meanFingerprints(), 1)
                            + " fingerprints per segment",
                    distinctFingerprints + " distinct of " + totalFingerprints + " fingerprints (selectivity "
                            + Floats.fixed(selectivity(), 2) + ")",
                    "widest posting covers " + widestPosting + " segments (" + Floats.percent(widestPostingShare(), 1)
                            + " of the corpus)",
                    "engine digest " + digest);
        }
    }

    /** Measure {@code index} in one pass over its records. */
    public static CorpusStats describe(Index index) {
        Map<String, Integer> jurisdictions = new LinkedHashMap<>();
        Map<String, Integer> sessions = new LinkedHashMap<>();
        Map<Long, Integer> postings = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int segments = 0;
        long tokens = 0;
        long total = 0;
        for (SegmentFingerprint record : index) {
            segments++;
            tokens += record.tokenCount();
            if (seen.add(record.key().slug())) {
                jurisdictions.merge(record.key().jurisdiction(), 1, Integer::sum);
                sessions.merge(record.key().session(), 1, Integer::sum);
            }
            List<Long> values = record.fingerprintValues();
            total += values.size();
            new HashSet<>(values).forEach(value -> postings.merge(value, 1, Integer::sum));
        }
        int widest =
                postings.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new CorpusStats(
                seen.size(),
                segments,
                jurisdictions,
                sessions,
                tokens,
                total,
                postings.size(),
                widest,
                index.config().digest());
    }
}

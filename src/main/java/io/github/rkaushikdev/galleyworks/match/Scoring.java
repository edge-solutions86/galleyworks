package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Similarity scores over fingerprint sets.
 *
 * <p>Jaccard is symmetric and containment is not, and the difference matters here. A model bill
 * copied whole into a one-section local ordinance has low Jaccard, because the ordinance is a small
 * part of the act, and containment near one, because nearly all of the ordinance came from the act.
 * Reuse detection wants containment; deduplication wants Jaccard. Duplicates never change a score.
 */
public final class Scoring {

    private Scoring() {}

    /**
     * Every score for one pair of segments, computed once.
     *
     * @param shared fingerprints in both sets
     * @param querySize distinct fingerprints in the query
     * @param otherSize distinct fingerprints in the other segment
     * @param jaccard shared over combined
     * @param queryContainment how much of the query appears in the other
     * @param otherContainment how much of the other appears in the query
     */
    public record PairScore(
            int shared,
            int querySize,
            int otherSize,
            double jaccard,
            double queryContainment,
            double otherContainment) {

        /** True when the sets share nothing. */
        public boolean isEmpty() {
            return shared == 0;
        }

        /** The larger of the two containments. */
        public double bestContainment() {
            return Math.max(queryContainment, otherContainment);
        }

        /** Which way the copying most likely ran, on set sizes alone. */
        public String direction() {
            if (queryContainment > otherContainment) {
                return "query-into-other";
            }
            if (otherContainment > queryContainment) {
                return "other-into-query";
            }
            return "even";
        }
    }

    /** Symmetric similarity: shared over combined; zero for two empty sets. */
    public static double jaccard(Collection<Long> left, Collection<Long> right) {
        Set<Long> a = new HashSet<>(left);
        Set<Long> b = new HashSet<>(right);
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection(a, b) / union.size();
    }

    /** How much of {@code left} appears in {@code right}. */
    public static double containment(Collection<Long> left, Collection<Long> right) {
        Set<Long> a = new HashSet<>(left);
        if (a.isEmpty()) {
            return 0.0;
        }
        return (double) intersection(a, new HashSet<>(right)) / a.size();
    }

    /** Shared over the smaller of the two sets. */
    public static double overlapCoefficient(Collection<Long> left, Collection<Long> right) {
        Set<Long> a = new HashSet<>(left);
        Set<Long> b = new HashSet<>(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        return (double) intersection(a, b) / Math.min(a.size(), b.size());
    }

    /** Score one pair of fingerprint sets, neither of which may be empty. */
    public static PairScore scorePair(Collection<Long> query, Collection<Long> other) {
        Set<Long> a = new HashSet<>(query);
        Set<Long> b = new HashSet<>(other);
        if (a.isEmpty() || b.isEmpty()) {
            throw new MatchError(
                    "cannot score an empty fingerprint set", Context.of("query", a.size(), "other", b.size()));
        }
        int shared = intersection(a, b);
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return new PairScore(
                shared,
                a.size(),
                b.size(),
                (double) shared / union.size(),
                (double) shared / a.size(),
                (double) shared / b.size());
    }

    private static int intersection(Set<Long> a, Set<Long> b) {
        int shared = 0;
        for (Long value : a) {
            if (b.contains(value)) {
                shared++;
            }
        }
        return shared;
    }
}

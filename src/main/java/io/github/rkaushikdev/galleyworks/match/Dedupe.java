package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolving passages and matches that describe the same finding twice.
 *
 * <p>A subsection is indexed and so is the section that contains it, so one copied clause is
 * reported once against the subsection and again against its parent. Resolution is by coverage, not
 * by score: a passage is dropped when a longer passage already covers most of it, and a match is
 * dropped when another match on the same instruments covers the same region. Keeping the longer of
 * the two keeps the parent when the whole section was copied and the subsection when only it was.
 */
public final class Dedupe {

    /** The default share of the shorter side two passages must share to be one finding. */
    public static final double DEFAULT_MIN_OVERLAP = 0.6;

    private static final Comparator<Passage> LONGEST_FIRST = Comparator.comparingInt(Passage::length)
            .reversed()
            .thenComparingInt(passage -> passage.query().start());
    private static final Comparator<SegmentMatch> LONGEST_MATCH_FIRST = Comparator.comparingInt(
                    SegmentMatch::longestPassage)
            .reversed()
            .thenComparing(match -> match.query().slug())
            .thenComparing(match -> match.other().slug());
    private static final Comparator<SegmentMatch> BY_SLUGS = Comparator.comparing(
                    (SegmentMatch match) -> match.query().slug())
            .thenComparing(match -> match.other().slug());

    private Dedupe() {}

    /** How much of the shorter span the two share, in {@code [0, 1]}. */
    public static double overlapFraction(TextSpan left, TextSpan right) {
        int shorter = Math.min(left.length(), right.length());
        if (shorter == 0) {
            return 0.0;
        }
        Optional<TextSpan> shared = left.intersection(right);
        return shared.map(span -> (double) span.length() / shorter).orElse(0.0);
    }

    /** Keep the longest passage from each overlapping group, with the default overlap. */
    public static List<Passage> dedupePassages(Collection<Passage> passages) {
        return dedupePassages(passages, DEFAULT_MIN_OVERLAP);
    }

    /** Keep the longest passage from each overlapping group, in query order. */
    public static List<Passage> dedupePassages(Collection<Passage> passages, double minOverlap) {
        if (!(minOverlap > 0.0 && minOverlap <= 1.0)) {
            throw new MatchError("min_overlap must lie in (0, 1]", Context.of("min_overlap", minOverlap));
        }
        List<Passage> ordered = new ArrayList<>(passages);
        ordered.sort(LONGEST_FIRST);
        List<Passage> kept = new ArrayList<>();
        for (Passage passage : ordered) {
            boolean covered = kept.stream().anyMatch(other -> samePlace(passage, other, minOverlap));
            if (!covered) {
                kept.add(passage);
            }
        }
        kept.sort(Comparator.comparingInt(passage -> passage.query().start()));
        return kept;
    }

    /** Drop matches whose region is already reported by a longer match, with the default overlap. */
    public static List<SegmentMatch> dedupeMatches(Collection<SegmentMatch> matches) {
        return dedupeMatches(matches, DEFAULT_MIN_OVERLAP);
    }

    /** Drop matches whose region is already reported by a longer match. */
    public static List<SegmentMatch> dedupeMatches(Collection<SegmentMatch> matches, double minOverlap) {
        List<SegmentMatch> ordered = new ArrayList<>(matches);
        ordered.sort(LONGEST_MATCH_FIRST);
        List<SegmentMatch> kept = new ArrayList<>();
        for (SegmentMatch match : ordered) {
            if (kept.stream().noneMatch(other -> sameFinding(match, other, minOverlap))) {
                kept.add(match);
            }
        }
        kept.sort(BY_SLUGS);
        return kept;
    }

    private static boolean samePlace(Passage one, Passage two, double minOverlap) {
        return overlapFraction(one.query(), two.query()) >= minOverlap
                && overlapFraction(one.other(), two.other()) >= minOverlap;
    }

    private static boolean sameFinding(SegmentMatch left, SegmentMatch right, double minOverlap) {
        if (!left.query().key().equals(right.query().key())
                || !left.other().key().equals(right.other().key())) {
            return false;
        }
        for (Passage one : left.passages()) {
            for (Passage two : right.passages()) {
                if (samePlace(one, two, minOverlap)) {
                    return true;
                }
            }
        }
        return false;
    }
}

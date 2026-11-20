package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.fingerprint.Shingle;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turning shared fingerprints into passages.
 *
 * <p>Anchors on their own are not a finding, because a common phrase produces one anywhere. A
 * report needs a passage, a run of anchors that advances through both documents together. Chaining
 * is greedy and monotone: anchors are walked in query order and appended to the open chain while the
 * other document also moves forward and neither gap exceeds {@code maxGap}. A jump backwards ends
 * the chain, because a passage that reorders text is two passages, not one.
 */
public final class Align {

    /** The default largest gap, in shingle positions, inside one passage. */
    public static final int DEFAULT_MAX_GAP = 40;

    private static final Comparator<Passage> LONGEST_FIRST = Comparator.comparingInt(Passage::length)
            .reversed()
            .thenComparingInt(passage -> passage.query().start());

    private Align() {}

    /** Every fingerprint the two segments share, in query order. */
    public static List<Anchor> anchorsBetween(SegmentFingerprint query, SegmentFingerprint other) {
        Map<Long, Shingle> otherByValue = new HashMap<>();
        for (Shingle shingle : other.selected()) {
            otherByValue.put(shingle.value(), shingle);
        }
        List<Anchor> found = new ArrayList<>();
        for (Shingle shingle : query.selected()) {
            Shingle partner = otherByValue.get(shingle.value());
            if (partner != null) {
                found.add(new Anchor(
                        shingle.position(), partner.position(), shingle.value(), shingle.span(), partner.span()));
            }
        }
        found.sort(null);
        return found;
    }

    /** Group anchors into monotone chains. */
    public static List<List<Anchor>> chainAnchors(List<Anchor> anchors, int maxGap) {
        if (maxGap < 0) {
            throw new MatchError("max_gap must not be negative", Context.of("max_gap", maxGap));
        }
        List<List<Anchor>> chains = new ArrayList<>();
        List<Anchor> current = new ArrayList<>();
        for (Anchor anchor : anchors) {
            if (current.isEmpty()) {
                current.add(anchor);
                continue;
            }
            Anchor previous = current.getLast();
            boolean forward = anchor.otherPosition() > previous.otherPosition();
            int queryGap = anchor.queryPosition() - previous.queryPosition();
            int otherGap = anchor.otherPosition() - previous.otherPosition();
            if (forward && queryGap <= maxGap && otherGap <= maxGap) {
                current.add(anchor);
            } else {
                chains.add(current);
                current = new ArrayList<>(List.of(anchor));
            }
        }
        if (!current.isEmpty()) {
            chains.add(current);
        }
        return chains;
    }

    /** Align two segments into the passages they share, needing two anchors and a gap of 40. */
    public static List<Passage> align(SegmentFingerprint query, SegmentFingerprint other) {
        return align(query, other, DEFAULT_MAX_GAP, 2);
    }

    /** Align two segments into the passages they share, longest first. */
    public static List<Passage> align(SegmentFingerprint query, SegmentFingerprint other, int maxGap, int minAnchors) {
        if (minAnchors < 1) {
            throw new MatchError("min_anchors must be at least one", Context.of("min_anchors", minAnchors));
        }
        List<Passage> passages = new ArrayList<>();
        for (List<Anchor> group : chainAnchors(anchorsBetween(query, other), maxGap)) {
            if (group.size() < minAnchors) {
                continue;
            }
            passages.add(new Passage(
                    hull(group.stream().map(Anchor::query).toList()),
                    hull(group.stream().map(Anchor::other).toList()),
                    group));
        }
        passages.sort(LONGEST_FIRST);
        return passages;
    }

    private static TextSpan hull(List<TextSpan> spans) {
        int start = spans.stream().mapToInt(TextSpan::start).min().orElseThrow();
        int end = spans.stream().mapToInt(TextSpan::end).max().orElseThrow();
        return new TextSpan(start, end);
    }
}

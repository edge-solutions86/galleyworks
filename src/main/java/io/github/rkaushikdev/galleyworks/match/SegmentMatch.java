package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.index.SegmentRef;
import io.github.rkaushikdev.galleyworks.match.Scoring.PairScore;
import java.util.List;

/**
 * One scored, aligned pair of segments: a finding.
 *
 * @param query the segment being checked
 * @param queryPath its printed path
 * @param other the indexed segment it matched
 * @param otherPath that segment's printed path
 * @param score the set scores of the pair
 * @param passages the reportable passages, longest first
 */
public record SegmentMatch(
        SegmentRef query,
        String queryPath,
        SegmentRef other,
        String otherPath,
        PairScore score,
        List<Passage> passages) {

    /** Copies the passages. */
    public SegmentMatch {
        passages = List.copyOf(passages);
    }

    /** How much of the query appears in the other segment. */
    public double containment() {
        return score.queryContainment();
    }

    /** The length of the longest passage, or zero. */
    public int longestPassage() {
        return passages.stream().mapToInt(Passage::length).max().orElse(0);
    }

    /** Number of passages. */
    public int passageCount() {
        return passages.size();
    }

    /** Distinct fingerprints across every passage. */
    public int distinctAnchors() {
        return (int) passages.stream()
                .flatMap(passage -> passage.anchors().stream())
                .mapToLong(Anchor::value)
                .distinct()
                .count();
    }

    /** True when the two instruments come from different jurisdictions. */
    public boolean crossesJurisdictions() {
        return !query.key().jurisdiction().equals(other.key().jurisdiction());
    }
}

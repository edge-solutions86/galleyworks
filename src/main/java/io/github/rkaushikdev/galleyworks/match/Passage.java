package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;

/**
 * A run of anchors advancing through both documents together: the unit a reuse report quotes.
 *
 * @param query the passage in the query instrument
 * @param other the passage in the other instrument
 * @param anchors the shared fingerprints it rests on, in query order
 */
public record Passage(TextSpan query, TextSpan other, List<Anchor> anchors) {

    /** Copies the anchors. */
    public Passage {
        anchors = List.copyOf(anchors);
    }

    /** Number of anchors. */
    public int anchorCount() {
        return anchors.size();
    }

    /** Characters on the query side. */
    public int queryLength() {
        return query.length();
    }

    /** Characters on the other side. */
    public int otherLength() {
        return other.length();
    }

    /** The shorter of the two sides, which is what a passage really covers. */
    public int length() {
        return Math.min(query.length(), other.length());
    }
}

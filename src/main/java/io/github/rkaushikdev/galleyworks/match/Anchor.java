package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.Comparator;

/**
 * One fingerprint shared by two segments: the same run of tokens at some position in each. Anchors
 * order by query position, then other position, value, and the two spans.
 *
 * @param queryPosition the shingle position in the query segment
 * @param otherPosition the shingle position in the other segment
 * @param value the shared fingerprint
 * @param query where it is printed in the query instrument
 * @param other where it is printed in the other instrument
 */
public record Anchor(int queryPosition, int otherPosition, long value, TextSpan query, TextSpan other)
        implements Comparable<Anchor> {

    private static final Comparator<Anchor> ORDER = Comparator.comparingInt(Anchor::queryPosition)
            .thenComparingInt(Anchor::otherPosition)
            .thenComparing(Anchor::value, Long::compareUnsigned)
            .thenComparing(Anchor::query)
            .thenComparing(Anchor::other);

    @Override
    public int compareTo(Anchor other) {
        return ORDER.compare(this, other);
    }
}

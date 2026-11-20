package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.util.Comparator;

/**
 * Points at one indexed segment. References order by instrument key, then segment index.
 *
 * @param key the instrument
 * @param segmentIndex the segment within it
 */
public record SegmentRef(InstrumentKey key, int segmentIndex) implements Comparable<SegmentRef> {

    private static final Comparator<SegmentRef> ORDER =
            Comparator.comparing(SegmentRef::key).thenComparingInt(SegmentRef::segmentIndex);

    /** {@code wy/2025/lower/HB0142#7}. */
    public String slug() {
        return key.slug() + "#" + segmentIndex;
    }

    /** The reference to the segment {@code record} describes. */
    public static SegmentRef of(SegmentFingerprint record) {
        return new SegmentRef(record.key(), record.segmentIndex());
    }

    /** Read a reference back from its slug. */
    public static SegmentRef parse(String slug) {
        int hash = slug.lastIndexOf('#');
        return new SegmentRef(
                InstrumentKey.parse(slug.substring(0, Math.max(hash, 0))), Integer.parseInt(slug.substring(hash + 1)));
    }

    @Override
    public int compareTo(SegmentRef other) {
        return ORDER.compare(this, other);
    }
}

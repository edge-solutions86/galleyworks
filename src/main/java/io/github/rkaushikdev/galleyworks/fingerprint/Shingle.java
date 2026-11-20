package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.util.TextSpan;

/**
 * One hashed window of the token stream, plus where it sits. Shingles order by position, then by
 * value read as an unsigned hash.
 *
 * @param value the unsigned 64-bit hash of the window
 * @param position the index of the window's first unit
 * @param span the characters the window covers
 */
public record Shingle(long value, int position, TextSpan span) implements Comparable<Shingle> {

    /** The same shingle located at {@code moved}. */
    public Shingle withSpan(TextSpan moved) {
        return new Shingle(value, position, moved);
    }

    @Override
    public int compareTo(Shingle other) {
        int order = Integer.compare(position, other.position);
        return order != 0 ? order : Long.compareUnsigned(value, other.value);
    }
}

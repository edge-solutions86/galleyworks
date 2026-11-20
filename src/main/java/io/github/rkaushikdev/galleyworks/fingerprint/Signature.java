package io.github.rkaushikdev.galleyworks.fingerprint;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

/**
 * A MinHash signature: one unsigned 64-bit minimum per permutation, as an immutable value.
 *
 * <p>The fraction of positions where two signatures agree estimates the Jaccard similarity of the
 * shingle sets behind them.
 */
public final class Signature {

    private final long[] values;

    private Signature(long[] values) {
        this.values = values;
    }

    /** A signature holding {@code values}, copied. */
    public static Signature of(long... values) {
        return new Signature(values.clone());
    }

    /** A signature holding {@code values}. */
    public static Signature of(List<Long> values) {
        return new Signature(values.stream().mapToLong(Long::longValue).toArray());
    }

    /** Number of permutations. */
    public int size() {
        return values.length;
    }

    /** True when the signature has no positions at all. */
    public boolean isEmpty() {
        return values.length == 0;
    }

    /** The minimum for permutation {@code index}. */
    public long get(int index) {
        return values[index];
    }

    /** A copy of the values. */
    public long[] toArray() {
        return values.clone();
    }

    /** The values, in permutation order. */
    public LongStream stream() {
        return Arrays.stream(values);
    }

    /** The positions {@code [from, to)} as a new signature. */
    public Signature slice(int from, int to) {
        return new Signature(Arrays.copyOfRange(values, from, to));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Signature signature && Arrays.equals(values, signature.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(Long.toUnsignedString(values[i]));
        }
        return out.append(')').toString();
    }
}

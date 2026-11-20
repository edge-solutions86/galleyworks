package io.github.rkaushikdev.galleyworks.util;

/**
 * Bit twiddling used by simhash and by LSH band extraction.
 *
 * <p>Hashes are unsigned 64-bit values held in a {@code long}: the bit pattern is the value, so
 * shifts are unsigned ({@code >>>}) and ordering, where it matters, is {@link
 * Long#compareUnsigned}.
 */
public final class Bitops {

    /** Width of every hash in the engine. */
    public static final int BITS64 = 64;

    /** All 64 bits set. */
    public static final long MASK64 = -1L;

    private Bitops() {}

    /** Number of set bits. */
    public static int popcount(long value) {
        return Long.bitCount(value);
    }

    /** Bit distance between two 64-bit values. */
    public static int hamming(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    /** Rotate left by {@code count} bits, modulo 64. */
    public static long rotl64(long value, int count) {
        return Long.rotateLeft(value, Math.floorMod(count, BITS64));
    }

    /** Rotate right by {@code count} bits, modulo 64. */
    public static long rotr64(long value, int count) {
        return rotl64(value, BITS64 - Math.floorMod(count, BITS64));
    }

    /** Split the value into {@code count} equal bands, most significant first. */
    public static long[] bands(long value, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("band count must be positive, got " + count);
        }
        if (BITS64 % count != 0) {
            throw new IllegalArgumentException("64 bits do not divide into " + count + " bands");
        }
        int width = BITS64 / count;
        long mask = width == BITS64 ? MASK64 : (1L << width) - 1;
        long[] chunks = new long[count];
        for (int index = 0; index < count; index++) {
            int shift = BITS64 - width * (index + 1);
            chunks[index] = (value >>> shift) & mask;
        }
        return chunks;
    }

    /** Extract a single band. */
    public static long bandOf(long value, int index, int count) {
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("band index " + index + " out of range for " + count + " bands");
        }
        return bands(value, count)[index];
    }

    /** Expand a 64-bit value to +1 / -1 per bit, most significant bit first. */
    public static int[] signVector(long value) {
        int[] vector = new int[BITS64];
        for (int i = 0; i < BITS64; i++) {
            vector[i] = ((value >>> (BITS64 - 1 - i)) & 1L) == 1L ? 1 : -1;
        }
        return vector;
    }

    /**
     * Reinterpret an unsigned 64-bit value as signed, the form a sqlite INTEGER column holds.
     *
     * <p>A {@code long} already is that signed reading of the bit pattern, so this is the identity;
     * it stays as the named seam every hash crosses on its way to a column.
     */
    public static long toSigned64(long value) {
        return value;
    }

    /** Reinterpret a signed 64-bit column value as the unsigned hash it stores. */
    public static long fromSigned64(long value) {
        return value;
    }

    /** Cosine-like similarity estimate implied by a simhash distance. */
    public static double similarityFromHamming(int distance) {
        if (distance < 0 || distance > BITS64) {
            throw new IllegalArgumentException("hamming distance " + distance + " outside [0, " + BITS64 + "]");
        }
        return 1.0 - (double) distance / BITS64;
    }
}

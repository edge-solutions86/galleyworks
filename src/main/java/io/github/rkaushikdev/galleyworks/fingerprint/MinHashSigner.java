package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.LshConfig;
import java.util.Arrays;
import java.util.Collection;

/**
 * Builds fixed-width MinHash signatures from shingle hashes.
 *
 * <p>The permutation family is affine over the Mersenne prime {@code 2^61 - 1}: {@code h_i(x) =
 * (a_i * x + b_i) mod p}, one multiply and an add per value per permutation, which is the hot loop
 * of a corpus pass. The family is derived deterministically from the configured seed, so two
 * indexes built with the same {@link LshConfig} produce comparable signatures on different machines.
 */
public final class MinHashSigner {

    /** The Mersenne prime {@code 2^61 - 1} the permutation family works over. */
    public static final long MERSENNE_PRIME = (1L << 61) - 1;

    /** The value every position of an empty set's signature holds: all 64 bits set. */
    public static final long EMPTY = -1L;

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final LshConfig config;
    private final long[] multipliers;
    private final long[] offsets;

    /** A signer under the default configuration. */
    public MinHashSigner() {
        this(new LshConfig());
    }

    /** A signer whose family is derived from {@code config}. */
    public MinHashSigner(LshConfig config) {
        this.config = config;
        int width = config.permutations();
        this.multipliers = new long[width];
        this.offsets = new long[width];
        long state = config.seed();
        for (int i = 0; i < width; i++) {
            state = Hashing.mix64(state + GOLDEN_GAMMA);
            // A multiplier of zero would collapse the permutation to a constant.
            multipliers[i] = 1 + Long.remainderUnsigned(state, MERSENNE_PRIME - 1);
            state = Hashing.mix64(state + GOLDEN_GAMMA);
            offsets[i] = Long.remainderUnsigned(state, MERSENNE_PRIME);
        }
    }

    /** Number of permutations in a signature. */
    public int width() {
        return config.permutations();
    }

    /** The configuration the family was derived from. */
    public LshConfig config() {
        return config;
    }

    long[] multipliers() {
        return multipliers.clone();
    }

    long[] offsets() {
        return offsets.clone();
    }

    /** The signature of the set {@code values}. */
    public Signature sign(Collection<Long> values) {
        return sign(values.stream().mapToLong(Long::longValue).toArray());
    }

    /** The signature of the set {@code values}; duplicates and order do not matter. */
    public Signature sign(long... values) {
        int width = width();
        long[] signature = new long[width];
        if (values.length == 0) {
            Arrays.fill(signature, EMPTY);
            return Signature.of(signature);
        }
        Arrays.fill(signature, MERSENNE_PRIME);
        for (long value : values) {
            long base = Long.remainderUnsigned(value, MERSENNE_PRIME);
            for (int index = 0; index < width; index++) {
                long candidate = affine(multipliers[index], base, offsets[index]);
                if (candidate < signature[index]) {
                    signature[index] = candidate;
                }
            }
        }
        return Signature.of(signature);
    }

    /** True when {@code signature} is the signature of the empty set. */
    public boolean isEmpty(Signature signature) {
        return signature.stream().allMatch(value -> value == EMPTY);
    }

    /** {@code (a * x + b) mod p} for {@code a, x, b < p}, exact over the full 122-bit product. */
    static long affine(long a, long x, long b) {
        long low = a * x;
        long high = Math.multiplyHigh(a, x);
        long folded = (low & MERSENNE_PRIME) + ((low >>> 61) | (high << 3)) + b;
        folded = (folded & MERSENNE_PRIME) + (folded >>> 61);
        return folded >= MERSENNE_PRIME ? folded - MERSENNE_PRIME : folded;
    }
}

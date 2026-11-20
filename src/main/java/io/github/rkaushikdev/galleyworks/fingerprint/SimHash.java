package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.util.Bitops;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SimHash, the cheap filter in front of the expensive comparison.
 *
 * <p>A SimHash is one 64-bit word per segment, and two segments that differ by a few words differ
 * by a few bits. It is far coarser than MinHash but costs one integer to store and one XOR plus a
 * popcount to compare. Weights matter: unweighted, a segment's SimHash is dominated by the
 * boilerplate every act repeats, so the caller supplies them.
 */
public final class SimHash {

    private SimHash() {}

    /**
     * One weighted 64-bit feature.
     *
     * @param value the feature's hash
     * @param weight how much it pulls on each bit
     */
    public record Feature(long value, double weight) {}

    /** Fold weighted 64-bit feature hashes into one 64-bit signature; no features sign as zero. */
    public static long simhash(List<Feature> features) {
        if (features.isEmpty()) {
            return 0L;
        }
        double[] accumulator = new double[Bitops.BITS64];
        for (Feature feature : features) {
            if (feature.weight() < 0) {
                throw new MatchError("simhash weights must not be negative", Context.of("weight", feature.weight()));
            }
            for (int bit = 0; bit < Bitops.BITS64; bit++) {
                if (((feature.value() >>> (Bitops.BITS64 - 1 - bit)) & 1L) == 1L) {
                    accumulator[bit] += feature.weight();
                } else {
                    accumulator[bit] -= feature.weight();
                }
            }
        }
        long signature = 0L;
        for (int bit = 0; bit < Bitops.BITS64; bit++) {
            if (accumulator[bit] > 0) {
                signature |= 1L << (Bitops.BITS64 - 1 - bit);
            }
        }
        return signature;
    }

    /** Hash {@code tokens}, each weighing one. */
    public static List<Feature> weightedFeatures(List<String> tokens) {
        return weightedFeatures(tokens, Map.of(), 0);
    }

    /**
     * Hash tokens and weight them by inverse document frequency.
     *
     * <p>With no corpus size every token weighs one, which is the right default for a single
     * document seen on its own.
     */
    public static List<Feature> weightedFeatures(
            List<String> tokens, Map<String, Integer> documentFrequency, int corpusSize) {
        List<Feature> features = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            double weight = 1.0;
            if (corpusSize > 0) {
                int seenIn = documentFrequency.getOrDefault(token, 0);
                weight = StrictMath.log((double) (corpusSize + 1) / (seenIn + 1)) + 1.0;
            }
            features.add(new Feature(Hashing.hashToken(token), weight));
        }
        return features;
    }

    /** SimHash of an unweighted token sequence. */
    public static long simhashOfTokens(List<String> tokens) {
        return simhash(weightedFeatures(tokens));
    }

    /** Bit distance between two signatures. */
    public static int distance(long left, long right) {
        return Bitops.hamming(left, right);
    }

    /** Similarity implied by the bit distance, in {@code [0, 1]}. */
    public static double similarity(long left, long right) {
        return Bitops.similarityFromHamming(distance(left, right));
    }
}

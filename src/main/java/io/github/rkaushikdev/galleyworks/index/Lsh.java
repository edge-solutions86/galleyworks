package io.github.rkaushikdev.galleyworks.index;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.fingerprint.Hashing;
import io.github.rkaushikdev.galleyworks.fingerprint.Signature;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Banded locality-sensitive hashing over MinHash signatures.
 *
 * <p>A signature of {@code b * r} positions is cut into {@code b} bands of {@code r} rows. Two
 * segments become candidates when any band matches exactly. The probability of that is {@code 1 -
 * (1 - s^r)^b} for true similarity {@code s}, the S-curve whose knee sits near {@code
 * (1/b)^(1/r)}. {@link #solveBanding} picks the split whose knee lands closest to a wanted
 * threshold, so an operator can ask for a similarity rather than for a banding.
 */
public final class Lsh {

    private static final int KEY_BYTES = 16;

    private Lsh() {}

    /** One bucket key per band of {@code signature}. */
    public static List<BandKey> bandKeys(Signature signature, LshConfig config) {
        if (signature.size() != config.permutations()) {
            throw new MatchError(
                    "signature width does not match the banding",
                    Context.of("width", signature.size(), "expected", config.permutations()));
        }
        int rows = config.rows();
        List<BandKey> keys = new ArrayList<>(config.bands());
        for (int index = 0; index < config.bands(); index++) {
            keys.add(bandKey(index, signature.slice(index * rows, (index + 1) * rows)));
        }
        return keys;
    }

    /**
     * Hash one band into a compact bucket key. The band index is folded in so that identical rows
     * in different bands do not share a bucket, which would inflate the candidate set for no benefit.
     */
    public static BandKey bandKey(int index, Signature rows) {
        ByteBuffer blob = ByteBuffer.allocate(4 + 8 * rows.size());
        blob.putInt(index);
        rows.stream().forEach(blob::putLong);
        return BandKey.of(Hashing.blake2b(blob.array(), KEY_BYTES));
    }

    /** Probability that a pair at {@code similarity} shares at least one band. */
    public static double candidateProbability(double similarity, int bands, int rows) {
        if (!(similarity >= 0.0 && similarity <= 1.0)) {
            throw new ConfigError("similarity must lie in [0, 1]", Context.of("similarity", similarity));
        }
        if (bands < 1 || rows < 1) {
            throw new ConfigError("bands and rows must both be positive", Context.of("bands", bands, "rows", rows));
        }
        return 1.0 - StrictMath.pow(1.0 - StrictMath.pow(similarity, rows), bands);
    }

    /** The banding of a 128-wide signature whose knee sits closest to {@code threshold}. */
    public static LshConfig solveBanding(double threshold) {
        return solveBanding(threshold, 128, 0x5EED);
    }

    /** The banding of {@code permutations} whose knee sits closest to {@code threshold}. */
    public static LshConfig solveBanding(double threshold, int permutations, long seed) {
        if (!(threshold > 0.0 && threshold < 1.0)) {
            throw new ConfigError("threshold must lie strictly inside (0, 1)", Context.of("threshold", threshold));
        }
        LshConfig best = null;
        double bestError = Double.POSITIVE_INFINITY;
        for (int bands = 1; bands <= permutations; bands++) {
            if (permutations % bands != 0) {
                continue;
            }
            int rows = permutations / bands;
            double knee = StrictMath.pow(1.0 / bands, 1.0 / rows);
            double error = Math.abs(knee - threshold);
            if (error < bestError) {
                bestError = error;
                best = new LshConfig(permutations, bands, seed);
            }
        }
        return best;
    }
}

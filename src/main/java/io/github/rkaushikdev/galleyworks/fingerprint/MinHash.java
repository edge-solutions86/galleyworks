package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import java.nio.ByteBuffer;

/**
 * Comparing and storing MinHash signatures.
 *
 * <p>Signatures are packed big-endian, so a packed signature sorts bytewise in the same order as
 * its unsigned values.
 */
public final class MinHash {

    private MinHash() {}

    /** Estimate Jaccard similarity from two signatures of equal width. */
    public static double jaccardEstimate(Signature left, Signature right) {
        if (left.size() != right.size()) {
            throw new MatchError(
                    "signatures have different widths", Context.of("left", left.size(), "right", right.size()));
        }
        if (left.isEmpty()) {
            throw new MatchError("cannot compare empty signatures");
        }
        int agree = 0;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) == right.get(i)) {
                agree++;
            }
        }
        return (double) agree / left.size();
    }

    /** Pack a signature for storage, big endian so it sorts bytewise. */
    public static byte[] signatureToBytes(Signature signature) {
        ByteBuffer out = ByteBuffer.allocate(8 * signature.size());
        signature.stream().forEach(out::putLong);
        return out.array();
    }

    /** Unpack a signature written by {@link #signatureToBytes}. */
    public static Signature signatureFromBytes(byte[] blob) {
        if (blob.length % 8 != 0) {
            throw new MatchError("signature blob is not a whole number of words", Context.of("size", blob.length));
        }
        ByteBuffer in = ByteBuffer.wrap(blob);
        long[] values = new long[blob.length / 8];
        for (int i = 0; i < values.length; i++) {
            values[i] = in.getLong();
        }
        return Signature.of(values);
    }
}

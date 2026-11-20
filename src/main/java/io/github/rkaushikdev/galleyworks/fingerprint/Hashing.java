package io.github.rkaushikdev.galleyworks.fingerprint;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Deterministic 64-bit hashing.
 *
 * <p>Fingerprints are written to disk and compared across processes and machines, so nothing here
 * may depend on per-process state. Everything is either a BLAKE2b digest or an explicit integer
 * mix, and both are stable forever. Values are unsigned 64-bit hashes held in a {@code long}.
 */
public final class Hashing {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;
    private static final long UNMIX_A = 0x319642B2D24D8EC3L; // modular inverse of MIX_B mod 2**64
    private static final long UNMIX_B = 0x96DE1B173F119089L; // modular inverse of MIX_A mod 2**64
    private static final byte NGRAM_SEPARATOR = 0x1f;

    /** The seed {@link #hashStream} folds from. */
    public static final long STREAM_SEED = 0xCBF29CE484222325L;

    private Hashing() {}

    /** The splitmix64 finalizer: a bijection on 64 bits with good avalanche. */
    public static long mix64(long value) {
        long x = value;
        x = (x ^ (x >>> 30)) * MIX_A;
        x = (x ^ (x >>> 27)) * MIX_B;
        return x ^ (x >>> 31);
    }

    /** Inverse of {@link #mix64}, used by tests and by index integrity checks. */
    public static long unmix64(long value) {
        long x = value;
        x = x ^ (x >>> 31) ^ (x >>> 62);
        x = x * UNMIX_A;
        x = x ^ (x >>> 27) ^ (x >>> 54);
        x = x * UNMIX_B;
        return x ^ (x >>> 30) ^ (x >>> 60);
    }

    /** A stable 64-bit digest of {@code data}: an 8-byte BLAKE2b read big-endian. */
    public static long hashBytes(byte[] data) {
        byte[] digest = Blake2b.digest(data, 8);
        long value = 0;
        for (byte b : digest) {
            value = (value << 8) | (b & 0xffL);
        }
        return value;
    }

    /** The unkeyed BLAKE2b digest of {@code data}, {@code length} bytes long (1 to 64). */
    public static byte[] blake2b(byte[] data, int length) {
        if (length < 1 || length > 64) {
            throw new IllegalArgumentException("BLAKE2b digests are 1 to 64 bytes, not " + length);
        }
        return Blake2b.digest(data, length);
    }

    /** A stable 64-bit digest of one canonical token. */
    public static long hashToken(String token) {
        return hashBytes(token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hash an ordered token n-gram.
     *
     * <p>The separator matters: without it {@code ["ab", "c"]} and {@code ["a", "bc"]} would
     * collide, and shingles of different widths would share fingerprints.
     */
    public static long hashNgram(List<String> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("cannot hash an empty n-gram");
        }
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                joined.write(NGRAM_SEPARATOR);
            }
            joined.writeBytes(tokens.get(i).getBytes(StandardCharsets.UTF_8));
        }
        return hashBytes(joined.toByteArray());
    }

    /** Derive an independent hash of {@code value} for permutation family {@code seed}. */
    public static long seededHash(long value, long seed) {
        return mix64(value ^ (seed * GOLDEN_GAMMA));
    }

    /** Fold a stream of 64-bit values into one digest, order sensitive. */
    public static long hashStream(Iterable<Long> values) {
        long acc = STREAM_SEED;
        for (long value : values) {
            acc = mix64(acc ^ value);
        }
        return acc;
    }

    /** {@code value} as 16 lowercase hex digits. */
    public static String hex(long value) {
        return String.format("%016x", value);
    }
}

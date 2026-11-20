package io.github.rkaushikdev.galleyworks.index;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * The bucket key of one LSH band: a 16-byte digest of the band's position and rows, compared by
 * value.
 */
public final class BandKey {

    private final byte[] bytes;

    private BandKey(byte[] bytes) {
        this.bytes = bytes;
    }

    /** A key holding a copy of {@code bytes}. */
    public static BandKey of(byte[] bytes) {
        return new BandKey(bytes.clone());
    }

    /** A copy of the key bytes. */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** Number of bytes in the key. */
    public int length() {
        return bytes.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BandKey key && Arrays.equals(bytes, key.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return HexFormat.of().formatHex(bytes);
    }
}

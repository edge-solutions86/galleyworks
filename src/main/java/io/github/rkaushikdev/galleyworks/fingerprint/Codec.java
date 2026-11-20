package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.io.ByteArrayOutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Binary encoding for fingerprint records, versioned from the first release.
 *
 * <p>Layout, all integers big endian: magic {@code LXDF} (4 bytes), version (1 byte), reserved
 * flags that must be zero (1 byte), key and path (u16 length + UTF-8 each), segment index, token
 * count (u32 each), simhash (u64), signature (u16 count + u64 each), selected shingles (u32 count +
 * u64 value, u32 position, u32 start, u32 end each). The canonical text is deliberately not stored:
 * it is derivable from the raw document and it is by far the largest field. A reader that meets a
 * version it does not know refuses rather than decoding a shorter record into a longer one.
 */
public final class Codec {

    /** The four bytes every record starts with. */
    public static final byte[] MAGIC = "LXDF".getBytes(StandardCharsets.US_ASCII);

    /** The layout version this codec writes and reads. */
    public static final int CODEC_VERSION = 1;

    private static final int HEADER_SIZE = 6;
    private static final int MAX_TEXT = 0xFFFF;

    private Codec() {}

    /** Serialize one fingerprint record. */
    public static byte[] encodeRecord(SegmentFingerprint record) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(MAGIC);
        out.write(CODEC_VERSION);
        out.write(0);
        putText(out, record.key().slug());
        putText(out, record.path());
        ByteBuffer body = ByteBuffer.allocate(4
                + 4
                + 8
                + 2
                + 8 * record.signature().size()
                + 4
                + 20 * record.selected().size());
        body.putInt(record.segmentIndex());
        body.putInt(record.tokenCount());
        body.putLong(record.simhash());
        body.putShort((short) record.signature().size());
        record.signature().stream().forEach(body::putLong);
        body.putInt(record.selected().size());
        for (Shingle shingle : record.selected()) {
            body.putLong(shingle.value());
            body.putInt(shingle.position());
            body.putInt(shingle.span().start());
            body.putInt(shingle.span().end());
        }
        out.writeBytes(body.array());
        return out.toByteArray();
    }

    private static void putText(ByteArrayOutputStream out, String value) {
        byte[] blob = value.getBytes(StandardCharsets.UTF_8);
        if (blob.length > MAX_TEXT) {
            throw new StoreError("fingerprint field is too long to encode", Context.of("size", blob.length));
        }
        out.write(blob.length >>> 8);
        out.write(blob.length & 0xFF);
        out.writeBytes(blob);
    }

    /** Rebuild a record written by {@link #encodeRecord}; its canonical text is empty. */
    public static SegmentFingerprint decodeRecord(byte[] blob) {
        if (blob.length < HEADER_SIZE) {
            throw new StoreError("fingerprint blob is too short to hold a header", Context.of("size", blob.length));
        }
        ByteBuffer in = ByteBuffer.wrap(blob);
        byte[] magic = new byte[4];
        in.get(magic);
        int version = Byte.toUnsignedInt(in.get());
        int flags = Byte.toUnsignedInt(in.get());
        if (!java.util.Arrays.equals(magic, MAGIC)) {
            throw new StoreError(
                    "fingerprint blob has the wrong magic",
                    Context.of("magic", HexFormat.of().formatHex(magic)));
        }
        if (version != CODEC_VERSION) {
            throw new StoreError(
                    "fingerprint blob was written by another codec version",
                    Context.of("found", version, "expected", CODEC_VERSION));
        }
        if (flags != 0) {
            throw new StoreError("fingerprint blob sets reserved flags", Context.of("flags", flags));
        }
        try {
            String slug = takeText(in);
            String path = takeText(in);
            int segmentIndex = in.getInt();
            int tokenCount = in.getInt();
            long simhash = in.getLong();
            int width = Short.toUnsignedInt(in.getShort());
            long[] signature = new long[width];
            for (int i = 0; i < width; i++) {
                signature[i] = in.getLong();
            }
            long count = Integer.toUnsignedLong(in.getInt());
            List<Shingle> selected = new ArrayList<>();
            for (long i = 0; i < count; i++) {
                long value = in.getLong();
                int position = in.getInt();
                int start = in.getInt();
                int end = in.getInt();
                selected.add(new Shingle(value, position, new TextSpan(start, end)));
            }
            if (in.hasRemaining()) {
                throw new StoreError("fingerprint blob has trailing bytes", Context.of("extra", in.remaining()));
            }
            return new SegmentFingerprint(
                    InstrumentKey.parse(slug),
                    segmentIndex,
                    path,
                    tokenCount,
                    Signature.of(signature),
                    simhash,
                    selected,
                    "");
        } catch (BufferUnderflowException exc) {
            throw new StoreError("fingerprint blob is truncated", Context.of("size", blob.length), exc);
        }
    }

    private static String takeText(ByteBuffer in) {
        int size = Short.toUnsignedInt(in.getShort());
        byte[] text = new byte[size];
        in.get(text);
        return new String(text, StandardCharsets.UTF_8);
    }

    /** Drop the canonical text, which the codec never writes. */
    public static SegmentFingerprint stripCanonical(SegmentFingerprint record) {
        return record.withoutCanonical();
    }
}

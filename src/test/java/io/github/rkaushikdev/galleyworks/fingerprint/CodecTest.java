package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.StoreError;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodecTest {

    private static final long ALL_BITS = 0xFFFFFFFFFFFFFFFFL;

    private static SegmentFingerprint record() {
        return record(
                "1(a)(ii)",
                Signature.of(1, 2, 3, ALL_BITS),
                0x0123456789ABCDEFL,
                List.of(
                        new Shingle(99, 0, new TextSpan(10, 40)),
                        new Shingle(Long.MIN_VALUE, 7, new TextSpan(41, 80))));
    }

    private static SegmentFingerprint record(String path, Signature signature, long simhash, List<Shingle> selected) {
        return new SegmentFingerprint(
                new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142"),
                4,
                path,
                128,
                signature,
                simhash,
                selected,
                "the board shall publish");
    }

    @Test
    void aRecordRoundTrips() {
        assertThat(Codec.decodeRecord(Codec.encodeRecord(record()))).isEqualTo(Codec.stripCanonical(record()));
    }

    @Test
    void theCanonicalTextIsNotStored() {
        assertThat(Codec.decodeRecord(Codec.encodeRecord(record())).canonical()).isEmpty();
    }

    @Test
    void theBlobStartsWithTheMagicAndVersion() {
        byte[] blob = Codec.encodeRecord(record());
        assertThat(Arrays.copyOfRange(blob, 0, 4)).isEqualTo(Codec.MAGIC);
        assertThat(blob[4]).isEqualTo((byte) Codec.CODEC_VERSION);
    }

    @Test
    void anEmptySelectionRoundTrips() {
        SegmentFingerprint empty = record("1(a)(ii)", record().signature(), record().simhash(), List.of());
        assertThat(Codec.decodeRecord(Codec.encodeRecord(empty)).selected()).isEmpty();
    }

    @Test
    void anEmptySignatureRoundTrips() {
        SegmentFingerprint empty = record("1(a)(ii)", Signature.of(), record().simhash(), record().selected());
        assertThat(Codec.decodeRecord(Codec.encodeRecord(empty)).signature()).isEqualTo(Signature.of());
    }

    @Test
    void fullWidthValuesSurvive() {
        SegmentFingerprint wide = record("1(a)(ii)", Signature.of(ALL_BITS), ALL_BITS, record().selected());
        assertThat(Codec.decodeRecord(Codec.encodeRecord(wide)).simhash()).isEqualTo(ALL_BITS);
    }

    @Test
    void shingleSpansSurvive() {
        SegmentFingerprint decoded = Codec.decodeRecord(Codec.encodeRecord(record()));
        assertThat(decoded.selected().get(1).span()).isEqualTo(new TextSpan(41, 80));
        assertThat(decoded.selected().get(1).position()).isEqualTo(7);
    }

    @Test
    void aShortBlobIsRejected() {
        assertThatThrownBy(() -> Codec.decodeRecord("LX".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("too short");
    }

    @Test
    void aForeignMagicIsRejected() {
        byte[] blob = Codec.encodeRecord(record());
        System.arraycopy("ZZZZ".getBytes(StandardCharsets.US_ASCII), 0, blob, 0, 4);
        assertThatThrownBy(() -> Codec.decodeRecord(blob))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("wrong magic");
    }

    @Test
    void anotherVersionIsRejected() {
        byte[] blob = Codec.encodeRecord(record());
        blob[4] = (byte) (Codec.CODEC_VERSION + 1);
        assertThatThrownBy(() -> Codec.decodeRecord(blob))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("another codec version")
                .satisfies(caught ->
                        assertThat(((StoreError) caught).context()).containsEntry("expected", Codec.CODEC_VERSION));
    }

    @Test
    void reservedFlagsAreRejected() {
        byte[] blob = Codec.encodeRecord(record());
        blob[5] = 1;
        assertThatThrownBy(() -> Codec.decodeRecord(blob))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("reserved flags");
    }

    @Test
    void trailingBytesAreRejected() {
        byte[] blob = Codec.encodeRecord(record());
        byte[] longer = Arrays.copyOf(blob, blob.length + 1);
        assertThatThrownBy(() -> Codec.decodeRecord(longer))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("trailing bytes");
    }

    @Test
    void aLongPathIsRejected() {
        SegmentFingerprint longPath =
                record("x".repeat(70000), record().signature(), record().simhash(), record().selected());
        assertThatThrownBy(() -> Codec.encodeRecord(longPath))
                .isInstanceOf(StoreError.class)
                .hasMessageContaining("too long to encode");
    }

    @Test
    void unicodeSurvivesTheRoundTrip() {
        SegmentFingerprint labelled = record("1(a)", record().signature(), record().simhash(), record().selected());
        SegmentFingerprint decoded = Codec.decodeRecord(Codec.encodeRecord(labelled));
        assertThat(decoded.path()).isEqualTo("1(a)");
        assertThat(decoded.key().slug()).isEqualTo("wy/2025/lower/HB0142");
    }
}

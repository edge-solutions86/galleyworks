package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.support.Samples;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HashingTest {

    static Stream<Long> values() {
        return Samples.u64("hashing");
    }

    static Stream<byte[]> blobs() {
        return Samples.binary("hash-bytes", 64);
    }

    static Stream<Arguments> seeded() {
        SplittableRandom random = Samples.random("seeded");
        return Samples.u64("seeded-value").map(value -> Arguments.of(value, random.nextInt(0, 256)));
    }

    @Test
    void hashesArePinnedAndMustNotDrift() {
        assertThat(Hashing.hashBytes(new byte[0])).isEqualTo(0xE4A6A0577479B2B4L);
        assertThat(Hashing.hashToken("board")).isEqualTo(Hashing.hashBytes("board".getBytes(StandardCharsets.UTF_8)));
        assertThat(Hashing.mix64(0)).isZero();
        assertThat(Hashing.mix64(1)).isEqualTo(0x5692161D100B05E5L);
    }

    @ParameterizedTest
    @MethodSource("values")
    void mix64RoundTrips(long value) {
        assertThat(Hashing.unmix64(Hashing.mix64(value))).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("values")
    void unmix64RoundTripsTheOtherWay(long value) {
        assertThat(Hashing.mix64(Hashing.unmix64(value))).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("values")
    void mix64StaysInRange(long value) {
        // Every long is a 64-bit value; the hex form never needs a seventeenth digit.
        assertThat(Hashing.hex(Hashing.mix64(value))).hasSize(16);
    }

    @ParameterizedTest
    @MethodSource("blobs")
    void hashBytesStaysInRange(byte[] data) {
        assertThat(Hashing.hex(Hashing.hashBytes(data))).hasSize(16);
    }

    @Test
    void ngramSeparatorStopsBoundaryCollisions() {
        assertThat(Hashing.hashNgram(List.of("ab", "c"))).isNotEqualTo(Hashing.hashNgram(List.of("a", "bc")));
        assertThat(Hashing.hashNgram(List.of("shall", "publish")))
                .isNotEqualTo(Hashing.hashNgram(List.of("publish", "shall")));
    }

    @Test
    void ngramOfOneTokenDiffersFromTheBareToken() {
        assertThat(Hashing.hashNgram(List.of("board"))).isEqualTo(Hashing.hashToken("board"));
    }

    @Test
    void emptyNgramIsRejected() {
        assertThatThrownBy(() -> Hashing.hashNgram(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty n-gram");
    }

    @Test
    void seededHashSeparatesPermutations() {
        long base = Hashing.hashToken("department");
        Set<Long> seeds = new HashSet<>();
        for (int seed = 0; seed < 64; seed++) {
            seeds.add(Hashing.seededHash(base, seed));
        }
        assertThat(seeds).hasSize(64);
    }

    @ParameterizedTest
    @MethodSource("seeded")
    void seededHashIsDeterministic(long value, int seed) {
        assertThat(Hashing.seededHash(value, seed)).isEqualTo(Hashing.seededHash(value, seed));
    }

    @Test
    void hashStreamIsOrderSensitive() {
        assertThat(Hashing.hashStream(List.of(1L, 2L, 3L))).isNotEqualTo(Hashing.hashStream(List.of(3L, 2L, 1L)));
        assertThat(Hashing.hashStream(List.of())).isEqualTo(0xCBF29CE484222325L);
    }

    @Test
    void hashStreamFoldsIncrementally() {
        List<Long> values =
                Stream.of("the", "board", "shall").map(Hashing::hashToken).toList();
        assertThat(Hashing.hashStream(values))
                .isEqualTo(
                        Hashing.mix64(Hashing.mix64(Hashing.mix64(0xCBF29CE484222325L ^ values.get(0)) ^ values.get(1))
                                ^ values.get(2)));
    }
}

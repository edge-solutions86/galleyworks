package io.github.rkaushikdev.galleyworks.util;

import static io.github.rkaushikdev.galleyworks.util.Bitops.BITS64;
import static io.github.rkaushikdev.galleyworks.util.Bitops.MASK64;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.support.Samples;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BitopsTest {

    static Stream<Arguments> pairs() {
        SplittableRandom random = Samples.random("hamming");
        return Samples.u64("hamming-left").map(left -> Arguments.of(left, random.nextLong()));
    }

    static Stream<Arguments> rotations() {
        SplittableRandom random = Samples.random("rotations");
        return Samples.u64("rotations-value").map(value -> Arguments.of(value, random.nextInt(0, 201)));
    }

    static Stream<Long> values() {
        return Samples.u64("values");
    }

    @Test
    void popcountMasksTo64Bits() {
        assertThat(Bitops.popcount(0)).isZero();
        assertThat(Bitops.popcount(MASK64)).isEqualTo(64);
        // A long has no 65th bit, so the original's (1 << 70) has nothing to add.
        assertThat(Bitops.popcount(MASK64 | Long.MIN_VALUE)).isEqualTo(64);
    }

    @Test
    void hammingIsSymmetricAndZeroOnEqual() {
        assertThat(Bitops.hamming(0b1011, 0b1011)).isZero();
        assertThat(Bitops.hamming(0b1011, 0b1000)).isEqualTo(2);
        assertThat(Bitops.hamming(0, MASK64)).isEqualTo(64);
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void hammingIsSymmetric(long left, long right) {
        assertThat(Bitops.hamming(left, right)).isEqualTo(Bitops.hamming(right, left));
    }

    @ParameterizedTest
    @MethodSource("rotations")
    void rotationsAreInverse(long value, int count) {
        assertThat(Bitops.rotr64(Bitops.rotl64(value, count), count)).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("values")
    void rotationPreservesPopcount(long value) {
        assertThat(Bitops.popcount(Bitops.rotl64(value, 17))).isEqualTo(Bitops.popcount(value));
    }

    @Test
    void rotationByZeroAndFullWidthIsIdentity() {
        assertThat(Bitops.rotl64(0xDEADBEEFL, 0)).isEqualTo(0xDEADBEEFL);
        assertThat(Bitops.rotl64(0xDEADBEEFL, 64)).isEqualTo(0xDEADBEEFL);
    }

    @Test
    void bandsSplitMostSignificantFirst() {
        long value = (0xABL << 56) | (0xCDL << 48);
        long[] chunks = Bitops.bands(value, 8);
        assertThat(chunks).hasSize(8);
        assertThat(chunks[0]).isEqualTo(0xAB);
        assertThat(chunks[1]).isEqualTo(0xCD);
        assertThat(chunks[2]).isZero();
    }

    @Test
    void bandsReassembleTheOriginal() {
        long value = 0x0123456789ABCDEFL;
        long rebuilt = 0;
        for (long chunk : Bitops.bands(value, 4)) {
            rebuilt = (rebuilt << 16) | chunk;
        }
        assertThat(rebuilt).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 7, 24})
    void bandsRejectsCountsThatDoNotDivide(int count) {
        assertThatThrownBy(() -> Bitops.bands(1, count)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bandOfAgreesWithBands() {
        long value = 0x0123456789ABCDEFL;
        long[] chunks = Bitops.bands(value, 8);
        long[] single =
                IntStream.range(0, 8).mapToLong(i -> Bitops.bandOf(value, i, 8)).toArray();
        assertThat(single).containsExactly(chunks);
    }

    @Test
    void bandOfRejectsOutOfRangeIndex() {
        assertThatThrownBy(() -> Bitops.bandOf(1, 8, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void signVectorMatchesBitOrder() {
        int[] vector = Bitops.signVector(1L << 63);
        assertThat(vector[0]).isEqualTo(1);
        assertThat(Arrays.stream(vector, 1, vector.length).distinct().toArray()).containsExactly(-1);
        assertThat(vector).hasSize(BITS64);
    }

    @Test
    void similarityFromHammingEndpoints() {
        assertThat(Bitops.similarityFromHamming(0)).isEqualTo(1.0);
        assertThat(Bitops.similarityFromHamming(64)).isEqualTo(0.0);
        assertThat(Bitops.similarityFromHamming(32)).isCloseTo(0.5, within(1e-6));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 65})
    void similarityRejectsImpossibleDistances(int distance) {
        assertThatThrownBy(() -> Bitops.similarityFromHamming(distance))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @ParameterizedTest
    @MethodSource("values")
    void signedConversionRoundTrips(long value) {
        assertThat(Bitops.fromSigned64(Bitops.toSigned64(value))).isEqualTo(value);
    }

    @Test
    void signedConversionFitsASqliteColumn() {
        long limit = Long.parseUnsignedLong("9223372036854775808");
        long mask = Long.parseUnsignedLong("18446744073709551615");
        assertThat(Bitops.toSigned64(0)).isZero();
        assertThat(Bitops.toSigned64(limit - 1)).isEqualTo(Long.MAX_VALUE);
        assertThat(Bitops.toSigned64(limit)).isEqualTo(Long.MIN_VALUE);
        assertThat(Bitops.toSigned64(mask)).isEqualTo(-1L);
        assertThat(Long.toUnsignedString(Bitops.fromSigned64(-1L))).isEqualTo("18446744073709551615");
    }
}

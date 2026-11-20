package io.github.rkaushikdev.galleyworks.fingerprint;

import static io.github.rkaushikdev.galleyworks.fingerprint.MinHashSigner.MERSENNE_PRIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.LshConfig;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.support.Samples;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MinhashTest {

    private static long[] values(String words) {
        return Arrays.stream(words.split(" ")).mapToLong(Hashing::hashToken).toArray();
    }

    private static MinHashSigner signer() {
        return signer(64);
    }

    private static MinHashSigner signer(int permutations) {
        return new MinHashSigner(new LshConfig(permutations, permutations / 4, 0x5EED));
    }

    static Stream<long[]> signatures() {
        SplittableRandom random = Samples.random("signature-bytes");
        List<long[]> lists = new ArrayList<>();
        while (lists.size() < Samples.EXAMPLES) {
            long[] signature = new long[random.nextInt(1, 17)];
            for (int i = 0; i < signature.length; i++) {
                signature[i] = random.nextLong();
            }
            lists.add(signature);
        }
        return lists.stream();
    }

    @Test
    void signatureWidthFollowsTheConfig() {
        assertThat(signer(32).width()).isEqualTo(32);
        assertThat(signer(32).sign(values("a b c")).size()).isEqualTo(32);
    }

    @Test
    void signingIsOrderIndependent() {
        assertThat(signer().sign(values("the board shall act")))
                .isEqualTo(signer().sign(values("act shall board the")));
    }

    @Test
    void signingIgnoresDuplicates() {
        assertThat(signer().sign(values("the board shall")))
                .isEqualTo(signer().sign(values("the board shall the board")));
    }

    @Test
    void signingIsDeterministicAcrossInstances() {
        assertThat(signer().sign(values("a b c"))).isEqualTo(signer().sign(values("a b c")));
    }

    @Test
    void aDifferentSeedGivesADifferentSignature() {
        Signature left = new MinHashSigner(new LshConfig().withSeed(1)).sign(values("a b c"));
        Signature right = new MinHashSigner(new LshConfig().withSeed(2)).sign(values("a b c"));
        assertThat(left).isNotEqualTo(right);
    }

    @Test
    void anEmptySetSignsAsEmpty() {
        Signature signature = signer().sign();
        assertThat(signature.stream().distinct().toArray()).containsExactly(-1L);
        assertThat(signer().isEmpty(signature)).isTrue();
    }

    @Test
    void identicalSetsEstimateOne() {
        Signature signature = signer(128).sign(values("the board shall publish a schedule"));
        assertThat(MinHash.jaccardEstimate(signature, signature)).isEqualTo(1.0);
    }

    @Test
    void disjointSetsEstimateNearZero() {
        Signature left = signer(128).sign(values("alpha bravo charlie delta echo"));
        Signature right = signer(128).sign(values("foxtrot golf hotel india juliett"));
        assertThat(MinHash.jaccardEstimate(left, right)).isLessThan(0.2);
    }

    @Test
    void aHalfSharedSetEstimatesNearAThird() {
        Signature left = signer(256).sign(values("a b c d e f g h"));
        Signature right = signer(256).sign(values("e f g h i j k l"));
        assertThat(MinHash.jaccardEstimate(left, right)).isStrictlyBetween(0.15, 0.5);
    }

    @Test
    void theEstimateRisesWithRealOverlap() {
        MinHashSigner wide = signer(256);
        Signature base = wide.sign(values("a b c d e f g h i j"));
        double close = MinHash.jaccardEstimate(base, wide.sign(values("a b c d e f g h i x")));
        double distant = MinHash.jaccardEstimate(base, wide.sign(values("a b c d e p q r s t")));
        assertThat(close).isGreaterThan(distant);
    }

    @Test
    void comparingDifferentWidthsIsRejected() {
        assertThatThrownBy(() -> MinHash.jaccardEstimate(Signature.of(1, 2, 3), Signature.of(1, 2)))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("different widths");
    }

    @Test
    void comparingEmptySignaturesIsRejected() {
        assertThatThrownBy(() -> MinHash.jaccardEstimate(Signature.of(), Signature.of()))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("empty signatures");
    }

    @ParameterizedTest
    @MethodSource("signatures")
    void signaturesRoundTripThroughBytes(long[] values) {
        byte[] blob = MinHash.signatureToBytes(Signature.of(values));
        assertThat(blob).hasSize(8 * values.length);
        assertThat(MinHash.signatureFromBytes(blob)).isEqualTo(Signature.of(values));
    }

    @Test
    void aTruncatedBlobIsRejected() {
        assertThatThrownBy(() ->
                        MinHash.signatureFromBytes("1234567".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("whole number of words");
    }

    @Test
    void packingIsBigEndian() {
        assertThat(MinHash.signatureToBytes(Signature.of(1))).containsExactly(0, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    void thePermutationFamilyIsWellFormed() {
        MinHashSigner family = signer(64);
        assertThat(Arrays.stream(family.multipliers()).boxed().toList())
                .allSatisfy(value -> assertThat(value).isBetween(1L, MERSENNE_PRIME - 1));
        assertThat(Arrays.stream(family.offsets()).boxed().toList())
                .allSatisfy(value -> assertThat(value).isBetween(0L, MERSENNE_PRIME - 1));
    }

    @Test
    void theFamilyHasNoRepeatedPermutation() {
        MinHashSigner family = signer(128);
        long[] multipliers = family.multipliers();
        long[] offsets = family.offsets();
        Set<List<Long>> pairs = new HashSet<>();
        for (int i = 0; i < multipliers.length; i++) {
            pairs.add(List.of(multipliers[i], offsets[i]));
        }
        assertThat(pairs).hasSize(128);
    }

    @Test
    void everySignatureValueIsInsideThePrime() {
        Signature signature = signer(64).sign(values("the board shall publish a schedule"));
        assertThat(signature.stream().boxed().toList())
                .allSatisfy(value -> assertThat(value).isBetween(0L, MERSENNE_PRIME - 1));
    }

    @Test
    void oneAddedElementCanOnlyLowerASignature() {
        MinHashSigner family = signer(64);
        long[] base = values("alpha bravo charlie");
        Signature before = family.sign(base);
        long[] more = Arrays.copyOf(base, base.length + 1);
        more[base.length] = values("delta")[0];
        Signature after = family.sign(more);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i)).isLessThanOrEqualTo(before.get(i));
        }
    }

    @Test
    void aSingletonSignatureIsThePermutationOfItsValue() {
        MinHashSigner family = signer(8);
        long value = values("board")[0];
        Signature signature = family.sign(value);
        BigInteger prime = BigInteger.valueOf(MERSENNE_PRIME);
        BigInteger base = new BigInteger(Long.toUnsignedString(value)).mod(prime);
        long[] expected = new long[8];
        for (int i = 0; i < 8; i++) {
            expected[i] = BigInteger.valueOf(family.multipliers()[i])
                    .multiply(base)
                    .add(BigInteger.valueOf(family.offsets()[i]))
                    .mod(prime)
                    .longValueExact();
        }
        assertThat(signature).isEqualTo(Signature.of(expected));
    }
}

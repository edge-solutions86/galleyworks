package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import io.github.rkaushikdev.galleyworks.support.Samples;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class WinnowTest {

    private static List<Shingle> fake(List<Long> values) {
        List<Shingle> shingles = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            shingles.add(new Shingle(values.get(index), index, new TextSpan(index, index + 1)));
        }
        return shingles;
    }

    private static List<Shingle> fake(long... values) {
        return fake(java.util.Arrays.stream(values).boxed().toList());
    }

    private static List<Shingle> shinglesOf(String text) {
        return Shingles.wordShingles(Tokens.wordsOnly(Tokens.tokenize(text)), 3);
    }

    private static Stream<List<Long>> lists(String property, int maxValue, int minSize, int maxSize) {
        SplittableRandom random = Samples.random(property);
        List<List<Long>> lists = new ArrayList<>();
        while (lists.size() < Samples.EXAMPLES) {
            int size = random.nextInt(minSize, maxSize + 1);
            List<Long> values = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                values.add((long) random.nextInt(maxValue + 1));
            }
            lists.add(values);
        }
        return lists.stream();
    }

    static Stream<List<Long>> shortLists() {
        return lists("winnow-subsequence", 50, 1, 40);
    }

    static Stream<List<Long>> longLists() {
        return lists("winnow-windows", 1000, 8, 60);
    }

    @Test
    void aWindowOfOneKeepsEverything() {
        List<Shingle> shingles = fake(5, 3, 9);
        assertThat(Winnow.winnow(shingles, 1)).isEqualTo(shingles);
    }

    @Test
    void theMinimumOfEachWindowIsSelected() {
        assertThat(Winnow.winnow(fake(7, 2, 9, 4, 8), 3).stream().map(Shingle::value))
                .containsExactly(2L, 4L);
    }

    @Test
    void aTieTakesTheRightmostOccurrence() {
        assertThat(Winnow.winnow(fake(1, 5, 1, 5), 3).stream().map(Shingle::position))
                .containsExactly(2);
    }

    @Test
    void aRepeatedSelectionIsRecordedOnce() {
        // The window at (1, 2) picks position 1 again; it is not recorded twice.
        assertThat(Winnow.winnow(fake(9, 1, 9, 9, 9), 2).stream().map(Shingle::position))
                .containsExactly(1, 3, 4);
    }

    @Test
    void aFallingSequenceSelectsEachNewMinimum() {
        assertThat(Winnow.winnow(fake(5, 4, 3, 2, 1), 2).stream().map(Shingle::value))
                .containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    void anEmptyInputYieldsNothing() {
        assertThat(Winnow.winnow(List.of(), 4)).isEmpty();
    }

    @Test
    void aWindowWiderThanTheInputStillSelectsOne() {
        assertThat(Winnow.winnow(fake(7, 2, 9), 10).stream().map(Shingle::value))
                .containsExactly(2L);
    }

    @Test
    void aZeroWindowIsRejected() {
        assertThatThrownBy(() -> Winnow.winnow(fake(1), 0))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void expectedDensityFallsWithTheWindow() {
        assertThat(Winnow.expectedDensity(1)).isEqualTo(1.0);
        assertThat(Winnow.expectedDensity(3)).isCloseTo(0.5, within(1e-9));
        assertThat(Winnow.expectedDensity(9)).isCloseTo(0.2, within(1e-9));
        assertThatThrownBy(() -> Winnow.expectedDensity(0)).isInstanceOf(MatchError.class);
    }

    @Test
    void guaranteeLengthIsTheSumLessOne() {
        assertThat(Winnow.guaranteeLength(5, 4)).isEqualTo(8);
        assertThatThrownBy(() -> Winnow.guaranteeLength(0, 4)).isInstanceOf(MatchError.class);
    }

    @ParameterizedTest
    @MethodSource("shortLists")
    void selectionIsASubsequenceOfTheInput(List<Long> values) {
        List<Shingle> shingles = fake(values);
        List<Shingle> selected = Winnow.winnow(shingles, 4);
        List<Integer> positions = selected.stream().map(Shingle::position).toList();
        assertThat(positions).isEqualTo(new ArrayList<>(new TreeSet<>(positions)));
        for (int position : positions) {
            assertThat(selected).contains(shingles.get(position));
        }
    }

    @ParameterizedTest
    @MethodSource("longLists")
    void everyWindowContributesASelection(List<Long> values) {
        int window = 4;
        Set<Integer> selected = Winnow.winnow(fake(values), window).stream()
                .map(Shingle::position)
                .collect(Collectors.toSet());
        for (int start = 0; start <= values.size() - window; start++) {
            Set<Integer> span = new HashSet<>();
            for (int position = start; position < start + window; position++) {
                span.add(position);
            }
            span.retainAll(selected);
            assertThat(span).isNotEmpty();
        }
    }

    @Test
    void aSharedRunSurvivesWinnowingInBothDocuments() {
        String shared = "the board shall publish a schedule of appropriations not later than sixty days";
        Set<Long> left = Winnow.winnow(shinglesOf("first preamble text here " + shared), 4).stream()
                .map(Shingle::value)
                .collect(Collectors.toSet());
        Set<Long> right = Winnow.winnow(shinglesOf(shared + " and a different tail follows"), 4).stream()
                .map(Shingle::value)
                .collect(Collectors.toCollection(HashSet::new));
        right.retainAll(left);
        assertThat(right).isNotEmpty();
    }

    @Test
    void selectionIsDeterministic() {
        List<Shingle> shingles = shinglesOf("the board shall publish a schedule of appropriations");
        assertThat(Winnow.winnow(shingles, 3)).isEqualTo(Winnow.winnow(shingles, 3));
    }
}

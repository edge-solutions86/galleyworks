package io.github.rkaushikdev.galleyworks.fingerprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.ShingleConfig;
import io.github.rkaushikdev.galleyworks.ShingleMode;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import io.github.rkaushikdev.galleyworks.canon.Tokens.Token;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ShingleTest {

    private static final String TEXT = "the board shall publish a schedule";

    private static List<Token> words() {
        return words(TEXT);
    }

    private static List<Token> words(String text) {
        return Tokens.wordsOnly(Tokens.tokenize(text));
    }

    private static Set<Long> values(List<Shingle> shingles) {
        return shingles.stream().map(Shingle::value).collect(Collectors.toSet());
    }

    @Test
    void wordShinglesCount() {
        assertThat(Shingles.wordShingles(words(), 3)).hasSize(4);
        assertThat(Shingles.wordShingles(words(), 6)).hasSize(1);
    }

    @Test
    void aWindowWiderThanTheTextYieldsNothing() {
        assertThat(Shingles.wordShingles(words(), 7)).isEmpty();
        assertThat(Shingles.charShingles("abc", 4)).isEmpty();
    }

    @Test
    void wordShingleSpansCoverTheirWindow() {
        List<Shingle> shingles = Shingles.wordShingles(words(), 2);
        assertThat(shingles.getFirst().span().sliceOf(TEXT)).isEqualTo("the board");
        assertThat(shingles.getLast().span().sliceOf(TEXT)).isEqualTo("a schedule");
    }

    @Test
    void wordShinglesArePositionedFromZero() {
        assertThat(Shingles.wordShingles(words(), 3).stream().map(Shingle::position))
                .containsExactly(0, 1, 2, 3);
    }

    @Test
    void wordShinglesIgnoreCase() {
        List<Long> lower = Shingles.wordShingles(words(TEXT), 3).stream()
                .map(Shingle::value)
                .toList();
        List<Long> upper = Shingles.wordShingles(words(TEXT.toUpperCase(Locale.ROOT)), 3).stream()
                .map(Shingle::value)
                .toList();
        assertThat(lower).isEqualTo(upper);
    }

    @Test
    void wordShinglesAreOrderSensitive() {
        Set<Long> forward = values(Shingles.wordShingles(words("the board shall"), 3));
        Set<Long> backward = values(Shingles.wordShingles(words("shall board the"), 3));
        assertThat(forward).isNotEqualTo(backward);
    }

    @Test
    void punctuationDoesNotEnterAWordShingle() {
        List<Long> withPunctuation = Shingles.wordShingles(words("the board, shall act"), 2).stream()
                .map(Shingle::value)
                .toList();
        List<Long> without = Shingles.wordShingles(words("the board shall act"), 2).stream()
                .map(Shingle::value)
                .toList();
        assertThat(withPunctuation).isEqualTo(without);
    }

    @Test
    void aSharedRunProducesSharedShingles() {
        Set<Long> left = values(Shingles.wordShingles(words("the board shall publish a schedule"), 3));
        Set<Long> right =
                new HashSet<>(values(Shingles.wordShingles(words("the commission shall publish a schedule"), 3)));
        right.retainAll(left);
        assertThat(right).hasSize(2);
    }

    @Test
    void charShinglesCountAndSpans() {
        List<Shingle> shingles = Shingles.charShingles("abcdef", 3);
        assertThat(shingles).hasSize(4);
        assertThat(shingles.getFirst().span().sliceOf("abcdef")).isEqualTo("abc");
    }

    @Test
    void charShinglesTakeAnOffset() {
        assertThat(Shingles.charShingles("abcd", 2, 10).getFirst().span().start())
                .isEqualTo(10);
    }

    @Test
    void zeroWidthIsRejected() {
        assertThatThrownBy(() -> Shingles.wordShingles(words(), 0))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> Shingles.charShingles("abc", 0))
                .isInstanceOf(MatchError.class)
                .hasMessageContaining("at least one");
    }

    @Test
    void shinglesForFollowsTheConfiguredMode() {
        List<Shingle> word = Shingles.shinglesFor(TEXT, new ShingleConfig().withWidth(3));
        List<Shingle> character = Shingles.shinglesFor(
                TEXT, new ShingleConfig().withMode(ShingleMode.CHARACTER).withWidth(3));
        assertThat(word).hasSize(4);
        assertThat(character).hasSize(TEXT.length() - 2);
    }

    @Test
    void shinglesForPassesTheOffsetThrough() {
        List<Shingle> shingles = Shingles.shinglesFor(TEXT, new ShingleConfig().withWidth(2), 100, true);
        assertThat(shingles.getFirst().span().start()).isEqualTo(100);
    }

    @Test
    void shinglesSortByPositionThenValue() {
        List<Shingle> shingles = Shingles.wordShingles(words(), 2);
        List<Shingle> sorted = new ArrayList<>(shingles);
        sorted.sort(null);
        assertThat(sorted).isEqualTo(shingles);
    }

    @Test
    void anEmptyTextHasNoShingles() {
        assertThat(Shingles.shinglesFor("", new ShingleConfig().withWidth(3))).isEmpty();
        assertThat(Shingles.shinglesFor(
                        "", new ShingleConfig().withMode(ShingleMode.CHARACTER).withWidth(3)))
                .isEmpty();
    }
}

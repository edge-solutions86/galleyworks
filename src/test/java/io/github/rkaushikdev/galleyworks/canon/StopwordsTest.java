package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.fingerprint.Shingle;
import io.github.rkaushikdev.galleyworks.fingerprint.Shingles;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StopwordsTest {

    private static final String CLAUSE = "the board shall publish a schedule of appropriations";

    private static List<String> words(String text) {
        return Tokens.wordsOnly(Tokens.tokenize(text)).stream()
                .map(Tokens.Token::lowered)
                .toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"the", "of", "shall", "may", "within"})
    void functionWordsAreStopwords(String token) {
        assertThat(Stopwords.isStopword(token)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"board", "schedule", "appropriation", "publish"})
    void subjectMatterIsNot(String token) {
        assertThat(Stopwords.isStopword(token)).isFalse();
    }

    @Test
    void caseDoesNotMatter() {
        assertThat(Stopwords.isStopword("The")).isTrue();
        assertThat(Stopwords.isStopword("SHALL")).isTrue();
    }

    @Test
    void theListIsLowercaseAndUnique() {
        assertThat(Stopwords.STOPWORDS).allSatisfy(word -> assertThat(word).isEqualTo(Text.lower(word)));
        assertThat(new HashSet<>(Stopwords.STOPWORDS)).hasSameSizeAs(Stopwords.STOPWORDS);
    }

    @Test
    void contentTokensDropsTheFunctionWords() {
        assertThat(Stopwords.contentTokens(words(CLAUSE)))
                .containsExactly("board", "publish", "schedule", "appropriations");
    }

    @Test
    void contentTokensOnAnEmptyInput() {
        assertThat(Stopwords.contentTokens(List.of())).isEmpty();
    }

    @Test
    void theContentRatioOfANormalClause() {
        assertThat(Stopwords.contentRatio(words(CLAUSE))).isStrictlyBetween(0.3, 0.6);
    }

    @Test
    void aRecitalHasALowContentRatio() {
        String recital = "notwithstanding any other provision of law and subject to the provisions of this act";
        assertThat(Stopwords.contentRatio(words(recital))).isLessThan(Stopwords.contentRatio(words(CLAUSE)));
    }

    @Test
    void anEmptyTokenStreamHasNoContent() {
        assertThat(Stopwords.contentRatio(List.of())).isEqualTo(0.0);
    }

    @Test
    void anAllContentStreamIsOne() {
        assertThat(Stopwords.contentRatio(List.of("board", "schedule"))).isEqualTo(1.0);
    }

    @Test
    void anAllStopwordStreamIsZero() {
        assertThat(Stopwords.contentRatio(List.of("the", "of", "and"))).isEqualTo(0.0);
    }

    @Test
    void shinglingStillKeepsTheFunctionWords() {
        List<Tokens.Token> tokens = Tokens.wordsOnly(Tokens.tokenize(CLAUSE));
        Set<Long> withStopwords =
                Shingles.wordShingles(tokens, 3).stream().map(Shingle::value).collect(Collectors.toSet());
        List<Tokens.Token> trimmed =
                Tokens.wordsOnly(Tokens.tokenize(String.join(" ", Stopwords.contentTokens(words(CLAUSE)))));
        Set<Long> without = Shingles.wordShingles(trimmed, 3).stream()
                .map(Shingle::value)
                .collect(Collectors.toCollection(HashSet::new));
        without.retainAll(withStopwords);
        assertThat(without).isEmpty();
    }

    @Test
    void theModalVerbsAreWeightedDownNotRemovedFromTheLanguage() {
        assertThat(Stopwords.isStopword("shall")).isTrue();
        assertThat(Stopwords.isStopword("may")).isTrue();
        assertThat(words("the board shall act")).contains("shall");
    }
}

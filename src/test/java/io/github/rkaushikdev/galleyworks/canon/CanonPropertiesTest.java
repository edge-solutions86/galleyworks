package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.support.Samples;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Properties the canonicalization pipeline has to hold for any input: the invariants the offset
 * map depends on, which is where a rule that looks right in isolation breaks the report that
 * quotes it.
 */
class CanonPropertiesTest {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123456789 .,;:()§-'\"\n\t “”—";

    static Stream<String> legalText() {
        return Samples.text("legal-text", ALPHABET, 200, 60);
    }

    static Stream<String> fewerLegalTexts() {
        return Samples.text("fewer-legal-texts", ALPHABET, 200, 40);
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void canonicalizationNeverRaises(String text) {
        assertThatCode(() -> Profiles.canonicalize(text)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void theMappingCoversTheResult(String text) {
        CanonResult result = Profiles.canonicalize(text);
        assertThat(result.mapping().targetLength()).isEqualTo(result.text().length());
        assertThat(result.mapping().sourceLength()).isEqualTo(text.length());
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void everyCanonicalOffsetMapsIntoTheSource(String text) {
        CanonResult result = Profiles.canonicalize(text);
        int step = Math.max(1, result.text().length() / 8);
        for (int offset = 0; offset <= result.text().length(); offset += step) {
            int source = result.mapping().toSource(offset);
            assertThat(source).isBetween(0, text.length());
        }
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void spansNeverInvert(String text) {
        CanonResult result = Profiles.canonicalize(text);
        int limit = result.text().length();
        int step = Math.max(1, limit / 6);
        for (int start = 0; start < limit; start += step) {
            int end = Math.min(limit, start + 5);
            TextSpan span = result.toSource(new TextSpan(start, end));
            assertThat(span.start()).isLessThanOrEqualTo(span.end());
            assertThat(span.end()).isLessThanOrEqualTo(text.length());
        }
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void canonicalizationIsIdempotentOnItsOwnOutput(String text) {
        String once = Profiles.canonicalize(text).text();
        String twice = Profiles.canonicalize(once).text();
        assertThat(twice).isEqualTo(once);
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void theVerbatimProfileOnlyTouchesTypography(String text) {
        CanonResult result = Profiles.canonicalize(text, Profiles.profile("verbatim"));
        assertThat(count(result.text(), '(')).isEqualTo(count(text, '('));
        assertThat(count(result.text(), ')')).isEqualTo(count(text, ')'));
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void theResultIsNeverLongerThanABoundedMultiple(String text) {
        CanonResult result = Profiles.canonicalize(text);
        assertThat(result.text().length()).isLessThanOrEqualTo(Math.max(32, text.length() * 4));
    }

    @ParameterizedTest
    @MethodSource("legalText")
    void tokenSpansStayInsideTheCanonicalText(String text) {
        CanonResult result = Profiles.canonicalize(text);
        for (Tokens.Token token : Tokens.tokenize(result.text())) {
            assertThat(token.span().end()).isLessThanOrEqualTo(result.text().length());
            assertThat(token.span().sliceOf(result.text())).isEqualTo(token.text());
        }
    }

    @ParameterizedTest
    @MethodSource("fewerLegalTexts")
    void disablingEveryRuleLeavesTheTextAlone(String text) {
        CanonConfig inert = new CanonConfig("default", false, false, false, false, false, false, false);
        CanonResult result = Profiles.buildPipeline().run(text, inert);
        assertThat(result.text().replace(" ", ""))
                .isEqualTo(text.replace(" ", "").replace("\n", "").replace("\t", ""));
    }

    @ParameterizedTest
    @MethodSource("fewerLegalTexts")
    void theAppliedTrailOnlyNamesRealRules(String text) {
        Set<String> known = new HashSet<>(Profiles.buildPipeline().ruleNames());
        assertThat(known).containsAll(Profiles.canonicalize(text).applied());
    }

    private static long count(String text, char wanted) {
        return text.chars().filter(c -> c == wanted).count();
    }
}

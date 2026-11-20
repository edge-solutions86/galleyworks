package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.support.Samples;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LabelsTest {

    static Stream<Integer> romanValues() {
        return Samples.ints("roman", 1, 3999);
    }

    @ParameterizedTest
    @CsvSource({"a, b", "h, i", "y, z", "z, aa", "aa, bb", "zz, aaa"})
    void alphaSuccessor(String label, String expected) {
        assertThat(Labels.alphaSuccessor(label)).isEqualTo(expected);
    }

    @Test
    void alphaSuccessorIsCaseInsensitive() {
        assertThat(Labels.alphaSuccessor("H")).isEqualTo("i");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a1", "ab", "!"})
    void alphaSuccessorRejectsNonSequences(String label) {
        assertThatThrownBy(() -> Labels.alphaSuccessor(label)).isInstanceOf(ParseError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"i", "iv", "xlii", "MCM"})
    void isRomanAcceptsRomanDigits(String label) {
        assertThat(Labels.isRoman(label)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a", "iz", "12"})
    void isRomanRejectsTheRest(String label) {
        assertThat(Labels.isRoman(label)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"i, 1", "ii, 2", "iv, 4", "ix, 9", "xiv, 14", "xl, 40", "mcmxcix, 1999"})
    void romanValue(String label, int value) {
        assertThat(Labels.romanValue(label)).isEqualTo(value);
    }

    @Test
    void romanValueRejectsNonCanonicalForms() {
        assertThatThrownBy(() -> Labels.romanValue("iiii"))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("canonical form");
        assertThatThrownBy(() -> Labels.romanValue("viiii"))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("canonical form");
    }

    @Test
    void romanValueRejectsLetters() {
        assertThatThrownBy(() -> Labels.romanValue("a"))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("not a roman label");
    }

    @ParameterizedTest
    @CsvSource({"1, i", "4, iv", "9, ix", "40, xl", "2025, mmxxv"})
    void toRoman(int value, String label) {
        assertThat(Labels.toRoman(value)).isEqualTo(label);
    }

    @Test
    void toRomanRejectsZeroAndNegatives() {
        assertThatThrownBy(() -> Labels.toRoman(0))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("start at one");
        assertThatThrownBy(() -> Labels.toRoman(-3))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("start at one");
    }

    @ParameterizedTest
    @MethodSource("romanValues")
    void romanRoundTrips(int value) {
        assertThat(Labels.romanValue(Labels.toRoman(value))).isEqualTo(value);
    }

    @ParameterizedTest
    @CsvSource({"i, ii", "iii, iv", "iv, v", "ix, x"})
    void romanSuccessor(String label, String expected) {
        assertThat(Labels.romanSuccessor(label)).isEqualTo(expected);
    }

    @Test
    void theTwoAlphabetsDisagreeAboutI() {
        assertThat(Labels.alphaSuccessor("h")).isEqualTo("i");
        assertThat(Labels.romanSuccessor("i")).isEqualTo("ii");
        assertThat(Labels.isRoman("i")).isTrue();
        assertThat(Labels.alphaSuccessor("i")).isEqualTo("j");
    }
}

package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.canon.Tokens.Token;
import io.github.rkaushikdev.galleyworks.canon.Tokens.TokenKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokensTest {

    private static List<TokenKind> kinds(String text) {
        return Tokens.tokenize(text).stream().map(Token::kind).toList();
    }

    private static List<String> texts(String text) {
        return Tokens.tokenize(text).stream().map(Token::text).toList();
    }

    @Test
    void spansIndexBackIntoTheSource() {
        String source = "The board shall publish.";
        for (Token token : Tokens.tokenize(source)) {
            assertThat(token.span().sliceOf(source)).isEqualTo(token.text());
        }
    }

    @Test
    void offsetShiftsEverySpan() {
        List<Token> shifted = Tokens.tokenize("board shall", 100);
        assertThat(shifted.stream().map(token -> token.span().start())).containsExactly(100, 106);
    }

    @Test
    void negativeOffsetIsRejected() {
        assertThatThrownBy(() -> Tokens.tokenize("x", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void hyphenatedWordsStayTogether() {
        assertThat(texts("sixty-five per centum")).containsExactly("sixty-five", "per", "centum");
    }

    @Test
    void apostrophesStayInsideTheWord() {
        assertThat(texts("the governor's office")).containsExactly("the", "governor's", "office");
    }

    @Test
    void trailingHyphenIsPunctuation() {
        assertThat(texts("water- rights")).containsExactly("water", "-", "rights");
    }

    @Test
    void sectionSignIsItsOwnToken() {
        List<Token> tokens = Tokens.tokenize("§ 41-3-101");
        assertThat(tokens.get(0).kind()).isSameAs(TokenKind.SECTION_SIGN);
        assertThat(tokens.get(1).text()).isEqualTo("41-3-101");
    }

    @Test
    void repeatedSectionSignsAreOneToken() {
        assertThat(Tokens.tokenize("§§ 1 and 2").getFirst().text()).isEqualTo("§§");
    }

    @Test
    void statuteNumbersKeepTheirInternalPunctuation() {
        assertThat(texts("W.S. 41-3-101(a)(ii)"))
                .containsExactly("W", ".", "S", ".", "41-3-101", "(", "a", ")", "(", "ii", ")");
    }

    @Test
    void ordinalsAreNumbers() {
        assertThat(kinds("1st")).containsExactly(TokenKind.NUMBER);
        assertThat(kinds("2,500.75")).containsExactly(TokenKind.NUMBER);
    }

    @Test
    void wordsOnlyDropsPunctuationAndSigns() {
        List<Token> tokens = Tokens.tokenize("§ 3. The board, however, shall act.");
        assertThat(Tokens.wordsOnly(tokens).stream().map(Token::text))
                .containsExactly("3", "The", "board", "however", "shall", "act");
    }

    @Test
    void loweredAndIsWordHelpers() {
        Token token = Tokens.tokenize("Board").getFirst();
        assertThat(token.isWord()).isTrue();
        assertThat(token.lowered()).isEqualTo("board");
    }

    @Test
    void emptyTextYieldsNothing() {
        assertThat(Tokens.tokenize("")).isEmpty();
        assertThat(Tokens.tokenize("   \n\t ")).isEmpty();
    }

    @Test
    void unicodeLettersAreWords() {
        assertThat(kinds("señor")).containsExactly(TokenKind.WORD);
    }
}

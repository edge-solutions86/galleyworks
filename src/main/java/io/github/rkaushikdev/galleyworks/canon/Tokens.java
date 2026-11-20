package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer for legislative prose.
 *
 * <p>Every token keeps its span: everything downstream, from shingling to the final report, needs
 * to point back at a character range in the raw document. The tokenizer is deliberately
 * conservative about what it joins: {@code sixty-five} is one word token, {@code W.S. 41-3-101} is
 * three tokens plus punctuation, and the section sign is its own token so a citation rule can find
 * it later.
 */
public final class Tokens {

    private static final String LETTER = "[\\p{L}\\p{Nl}\\p{No}]";
    private static final String WORD = LETTER + "(?:" + LETTER + "|['’‐-]?" + LETTER + ")*";
    private static final String NUMBER = "\\p{Nd}+(?:[.,\\-/]\\p{Nd}+)*(?:st|nd|rd|th)?";
    private static final Pattern TOKEN = Regex.compile("(?<section>§+)|(?<word>" + WORD + ")|(?<number>" + NUMBER
            + ")|(?<punct>[^" + Text.SPACE_CLASS.substring(1, Text.SPACE_CLASS.length() - 1) + "])");

    private Tokens() {}

    /** What kind of lexical unit a token is. */
    public enum TokenKind {
        WORD("word"),
        NUMBER("number"),
        PUNCT("punct"),
        SECTION_SIGN("section_sign");

        private final String value;

        TokenKind(String value) {
            this.value = value;
        }

        /** The lowercase name. */
        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * One lexical unit plus its span in the source text.
     *
     * @param text the characters as printed
     * @param span where they are printed
     * @param kind what kind of unit it is
     */
    public record Token(String text, TextSpan span, TokenKind kind) {

        /** True for a word token. */
        public boolean isWord() {
            return kind == TokenKind.WORD;
        }

        /** The token lowercased. */
        public String lowered() {
            return Text.lower(text);
        }
    }

    /** Split {@code text} into tokens. */
    public static List<Token> tokenize(String text) {
        return tokenize(text, 0);
    }

    /** Split {@code text} into tokens whose spans are shifted by {@code offset}. */
    public static List<Token> tokenize(String text, int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative, got " + offset);
        }
        List<Token> tokens = new ArrayList<>();
        Matcher match = TOKEN.matcher(text);
        while (match.find()) {
            TokenKind kind;
            if (match.group("section") != null) {
                kind = TokenKind.SECTION_SIGN;
            } else if (match.group("word") != null) {
                kind = TokenKind.WORD;
            } else if (match.group("number") != null) {
                kind = TokenKind.NUMBER;
            } else {
                kind = TokenKind.PUNCT;
            }
            tokens.add(new Token(match.group(), new TextSpan(match.start() + offset, match.end() + offset), kind));
        }
        return tokens;
    }

    /** The tokens a similarity comparison should see: words and numbers. */
    public static List<Token> wordsOnly(List<Token> tokens) {
        return tokens.stream()
                .filter(token -> token.kind() == TokenKind.WORD || token.kind() == TokenKind.NUMBER)
                .toList();
    }
}

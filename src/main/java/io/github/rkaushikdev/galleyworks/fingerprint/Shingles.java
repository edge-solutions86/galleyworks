package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.MatchError;
import io.github.rkaushikdev.galleyworks.ShingleConfig;
import io.github.rkaushikdev.galleyworks.ShingleMode;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import io.github.rkaushikdev.galleyworks.canon.Tokens.Token;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;

/**
 * Overlapping windows over canonical text.
 *
 * <p>A shingle is a fixed-width window of the token stream, hashed. Two documents share a shingle
 * when they share that exact run of tokens, so the shingle set is what containment and Jaccard are
 * computed over. Every shingle keeps the span it covers, because a match has to be reported as a
 * character range and not as a bag of hashes.
 */
public final class Shingles {

    /** The narrowest window adaptive shingling falls back to. */
    public static final int MIN_ADAPTIVE_WIDTH = 2;

    private Shingles() {}

    /** Hash every window of {@code width} consecutive tokens. */
    public static List<Shingle> wordShingles(List<Token> tokens, int width) {
        if (width < 1) {
            throw new MatchError("shingle width must be at least one", Context.of("width", width));
        }
        if (tokens.size() < width) {
            return List.of();
        }
        List<Shingle> out = new ArrayList<>();
        for (int start = 0; start <= tokens.size() - width; start++) {
            List<Token> window = tokens.subList(start, start + width);
            out.add(new Shingle(
                    Hashing.hashNgram(window.stream().map(Token::lowered).toList()),
                    start,
                    new TextSpan(
                            window.getFirst().span().start(),
                            window.getLast().span().end())));
        }
        return out;
    }

    /** Hash every window of {@code width} consecutive characters. */
    public static List<Shingle> charShingles(String text, int width) {
        return charShingles(text, width, 0);
    }

    /** Hash every window of {@code width} consecutive characters, spans shifted by {@code offset}. */
    public static List<Shingle> charShingles(String text, int width, int offset) {
        if (width < 1) {
            throw new MatchError("shingle width must be at least one", Context.of("width", width));
        }
        if (text.length() < width) {
            return List.of();
        }
        List<Shingle> out = new ArrayList<>();
        for (int start = 0; start <= text.length() - width; start++) {
            out.add(new Shingle(
                    Hashing.hashToken(text.substring(start, start + width)),
                    start,
                    new TextSpan(start + offset, start + width + offset)));
        }
        return out;
    }

    /**
     * The window to use for a text of {@code unitCount} units.
     *
     * <p>A subsection of nine words produces no five-word shingles at all under a fixed width, so it
     * is invisible to the index however distinctive it is. Narrowing the window for short text keeps
     * it findable; the floor of two stops the window collapsing to single words, which match
     * everywhere.
     */
    public static int effectiveWidth(int unitCount, ShingleConfig config) {
        if (unitCount >= config.width()) {
            return config.width();
        }
        return Math.max(MIN_ADAPTIVE_WIDTH, Math.min(config.width(), unitCount));
    }

    /** Shingle {@code text} the way {@code config} says to, adapting the width to short text. */
    public static List<Shingle> shinglesFor(String text, ShingleConfig config) {
        return shinglesFor(text, config, 0, true);
    }

    /** Shingle {@code text} the way {@code config} says to. */
    public static List<Shingle> shinglesFor(String text, ShingleConfig config, int offset, boolean adaptive) {
        if (config.mode() == ShingleMode.CHARACTER) {
            int width = adaptive ? effectiveWidth(text.length(), config) : config.width();
            return charShingles(text, width, offset);
        }
        List<Token> tokens = Tokens.wordsOnly(Tokens.tokenize(text, offset));
        int width = adaptive ? effectiveWidth(tokens.size(), config) : config.width();
        return wordShingles(tokens, width);
    }
}

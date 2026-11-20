package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a command line into words the way a POSIX shell quotes them, for reading back the words a
 * completion script passes in.
 *
 * <p>Whitespace separates words. Single quotes keep everything literally. Inside double quotes a
 * backslash escapes only a double quote or another backslash, and outside quotes it escapes any
 * character. The line being completed is usually unfinished, so an open quote or a trailing
 * backslash does not fail: the partial word is kept as it stands.
 */
public final class ShellWords {

    private static final String WHITESPACE = " \t\r\n";

    private ShellWords() {}

    /** The words of {@code line}. */
    public static List<String> split(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean inWord = false;
        char quote = 0;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else {
                    word.append(c);
                }
                i++;
            } else if (quote == '"') {
                if (c == '"') {
                    quote = 0;
                    i++;
                } else if (c == '\\') {
                    if (i + 1 >= line.length()) {
                        break;
                    }
                    char next = line.charAt(i + 1);
                    if (next != '"' && next != '\\') {
                        word.append(c);
                    }
                    word.append(next);
                    i += 2;
                } else {
                    word.append(c);
                    i++;
                }
            } else if (WHITESPACE.indexOf(c) >= 0) {
                if (inWord) {
                    words.add(word.toString());
                    word.setLength(0);
                    inWord = false;
                }
                i++;
            } else if (c == '\'' || c == '"') {
                quote = c;
                inWord = true;
                i++;
            } else if (c == '\\') {
                inWord = true;
                if (i + 1 >= line.length()) {
                    break;
                }
                word.append(line.charAt(i + 1));
                i += 2;
            } else {
                word.append(c);
                inWord = true;
                i++;
            }
        }
        if (inWord) {
            words.add(word.toString());
        }
        return words;
    }
}

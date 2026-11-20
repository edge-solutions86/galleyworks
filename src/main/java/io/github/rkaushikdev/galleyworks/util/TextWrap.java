package io.github.rkaushikdev.galleyworks.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Greedy paragraph wrapping with the line breaks the text report has always printed.
 *
 * <p>Text is split into chunks of whitespace and words, a word may break after a hyphen that sits
 * between letters, a word longer than a whole line is broken where it must be, and whitespace at
 * the start and end of each wrapped line is dropped. Tabs expand to the next multiple of eight and
 * every other ASCII whitespace character becomes a space before splitting.
 */
public final class TextWrap {

    private static final String WHITESPACE = "\t\n\u000b\f\r ";
    private static final String WS = "[\\t\\n\\x0B\\f\\r ]";
    private static final String NWS = "[^\\t\\n\\x0B\\f\\r ]";
    private static final String WORD_PUNCT = "[\\w!\"'&.,?]";
    private static final String LETTER = "[^\\d\\W]";
    private static final Pattern WORDSEP = Regex.compile("(" + WS + "+"
            + "|(?<=" + WORD_PUNCT + ")-{2,}(?=\\w)"
            + "|" + NWS + "+?(?:"
            + "-(?:(?<=" + LETTER + "{2}-)|(?<=" + LETTER + "-" + LETTER + "-))(?=" + LETTER + "-?" + LETTER + ")"
            + "|(?=" + WS + "|\\z)"
            + "|(?<=" + WORD_PUNCT + ")(?=-{2,}\\w)"
            + "))");
    private static final int TAB_SIZE = 8;

    private TextWrap() {}

    /** Wrap {@code text} to {@code width} columns with no indentation. */
    public static List<String> wrap(String text, int width) {
        return wrap(text, width, "", "");
    }

    /** Wrap {@code text} to {@code width} columns, indenting the first and later lines. */
    public static List<String> wrap(String text, int width, String initialIndent, String subsequentIndent) {
        if (width <= 0) {
            throw new IllegalArgumentException("invalid width " + width + " (must be > 0)");
        }
        List<String> chunks = split(munge(text));
        List<String> lines = new ArrayList<>();
        int next = 0;
        while (next < chunks.size()) {
            List<String> line = new ArrayList<>();
            int length = 0;
            String indent = lines.isEmpty() ? initialIndent : subsequentIndent;
            int room = width - indent.length();
            if (isBlank(chunks.get(next)) && !lines.isEmpty()) {
                next++;
            }
            while (next < chunks.size()) {
                int size = chunks.get(next).length();
                if (length + size <= room) {
                    line.add(chunks.get(next));
                    length += size;
                    next++;
                } else {
                    break;
                }
            }
            if (next < chunks.size() && chunks.get(next).length() > room) {
                String rest = breakLongWord(chunks.get(next), line, length, room);
                if (rest.isEmpty()) {
                    next++;
                } else {
                    chunks.set(next, rest);
                }
            }
            if (!line.isEmpty() && isBlank(line.getLast())) {
                line.removeLast();
            }
            if (!line.isEmpty()) {
                lines.add(indent + String.join("", line));
            }
        }
        return lines;
    }

    /** Put as much of {@code chunk} on {@code line} as fits, returning what is left over. */
    private static String breakLongWord(String chunk, List<String> line, int length, int room) {
        int spaceLeft = room < 1 ? 1 : room - length;
        int end = spaceLeft;
        if (chunk.length() > spaceLeft) {
            int hyphen = chunk.lastIndexOf('-', spaceLeft - 1);
            if (hyphen > 0 && chunk.substring(0, hyphen).chars().anyMatch(c -> c != '-')) {
                end = hyphen + 1;
            }
        }
        end = Math.max(0, Math.min(end, chunk.length()));
        line.add(chunk.substring(0, end));
        return chunk.substring(end);
    }

    private static boolean isBlank(String chunk) {
        return Text.strip(chunk).isEmpty();
    }

    private static String munge(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int column = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\t') {
                int spaces = TAB_SIZE - (column % TAB_SIZE);
                out.append(" ".repeat(spaces));
                column += spaces;
            } else if (c == '\n' || c == '\r') {
                out.append(' ');
                column = 0;
            } else if (WHITESPACE.indexOf(c) >= 0) {
                out.append(' ');
                column++;
            } else {
                out.append(c);
                column++;
            }
        }
        return out.toString();
    }

    private static List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        Matcher match = WORDSEP.matcher(text);
        int cursor = 0;
        while (match.find()) {
            if (match.start() > cursor) {
                chunks.add(text.substring(cursor, match.start()));
            }
            if (!match.group(1).isEmpty()) {
                chunks.add(match.group(1));
            }
            cursor = match.end();
        }
        if (cursor < text.length()) {
            chunks.add(text.substring(cursor));
        }
        return chunks;
    }
}

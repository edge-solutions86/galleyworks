package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LexLine;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LineKind;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;

/**
 * Cutting lexed lines into numbered sections. The split is deliberately dumb: a section starts at
 * a {@code Section N.} line and ends where the next one starts.
 */
public final class Blocks {

    private Blocks() {}

    /**
     * One numbered section and the lines that belong to it.
     *
     * @param label the printed section number
     * @param heading the statute catchline, or empty
     * @param span the section, without trailing whitespace
     * @param lines the lexed lines it owns
     */
    public record SectionBlock(String label, String heading, TextSpan span, List<LexLine> lines) {

        /** Copies the lines. */
        public SectionBlock {
            lines = List.copyOf(lines);
        }

        /** True when the section covers no text. */
        public boolean isEmpty() {
            return span.isEmpty();
        }
    }

    /** Cut the lexed lines into numbered sections. */
    public static List<SectionBlock> splitSections(String text, List<LexLine> lines) {
        List<LexLine> openers =
                lines.stream().filter(line -> line.kind() == LineKind.SECTION).toList();
        List<SectionBlock> blocks = new ArrayList<>();
        for (int position = 0; position < openers.size(); position++) {
            LexLine opener = openers.get(position);
            int following = position + 1 < openers.size()
                    ? openers.get(position + 1).span().start()
                    : text.length();
            List<LexLine> owned = lines.stream()
                    .filter(line -> opener.span().start() <= line.span().start()
                            && line.span().start() < following)
                    .toList();
            blocks.add(new SectionBlock(
                    opener.label(),
                    headingFor(text, owned),
                    new TextSpan(opener.span().start(), trimEnd(text, following)),
                    owned));
        }
        return blocks;
    }

    private static String headingFor(String text, List<LexLine> lines) {
        for (LexLine line : lines) {
            if (line.kind() == LineKind.CATCHLINE && line.body().isPresent()) {
                return Text.strip(line.body().get().sliceOf(text));
            }
        }
        return "";
    }

    /** Move {@code end} back over trailing whitespace. */
    public static int trimEnd(String text, int end) {
        int cursor = end;
        while (cursor > 0 && Text.isSpace(text.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }
}

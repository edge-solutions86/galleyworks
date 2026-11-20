package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules that remove structure the segment tree already records: enumeration labels and the
 * enacting clause.
 *
 * <p>Both are navigation, not substance. A clause copied from Wyoming into Montana arrives as
 * {@code (b)} where it left as {@code (a)}, and every act in a state opens with the same enacting
 * sentence, which would otherwise be the strongest match in the corpus. Neither is lost: the label
 * survives on the segment path, and the enacting clause survives in the raw text.
 */
public final class StructureRules {

    private static final Pattern LABEL = Regex.compile("\\((?<label>[A-Za-z0-9]{1,4})\\)\\s*");
    private static final List<String> OPENERS = List.of(". ", "; ", ": ", ") ", ".\n", ";\n");
    private static final Pattern ENACTING = Regex.ignoringCase("be it enacted[^.:]{0,200}[.:]\\s*");

    private StructureRules() {}

    private static boolean opensABlock(String text, int start) {
        if (start == 0) {
            return true;
        }
        for (String opener : OPENERS) {
            int from = start - opener.length();
            if (from >= 0 && text.startsWith(opener, from)) {
                return true;
            }
        }
        return false;
    }

    /** The spans of labels that introduce a block rather than cite one. */
    public static List<TextSpan> findEnumerationLabels(String text) {
        List<TextSpan> spans = new ArrayList<>();
        Matcher match = LABEL.matcher(text);
        while (match.find()) {
            if (opensABlock(text, match.start())) {
                spans.add(new TextSpan(match.start(), match.end()));
            }
        }
        return spans;
    }

    /** Drops {@code (a)}, {@code (ii)} and {@code (3)} labels that open a block. */
    public static final class EnumerationRule implements Rule {

        @Override
        public String name() {
            return "enumeration";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.dropEnumerationLabels()) {
                return List.of();
            }
            return findEnumerationLabels(text).stream()
                    .map(span -> new Edit(span, ""))
                    .toList();
        }
    }

    /** Drops the enacting clause every act in a jurisdiction shares. */
    public static final class EnactingClauseRule implements Rule {

        @Override
        public String name() {
            return "enacting-clause";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.dropEnactingClause()) {
                return List.of();
            }
            List<Edit> edits = new ArrayList<>();
            Matcher match = ENACTING.matcher(text);
            while (match.find()) {
                edits.add(new Edit(new TextSpan(match.start(), match.end()), ""));
            }
            return edits;
        }
    }
}

package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Folds internal cross-references such as {@code this act} and {@code the preceding subsection} to
 * one marker.
 *
 * <p>Every act says these and each means something different in each act, so left alone they are
 * strong shared fingerprints that carry no information about copying. They are folded to a marker
 * rather than deleted: deleting them would join the words on either side into a shingle that never
 * appeared in either document. A reference to a numbered section is left alone.
 */
public final class CrossReferenceRule implements Rule {

    /** The marker a self-reference folds to. */
    public static final String SELF_REFERENCE = "xref";

    private static final List<String> UNITS =
            List.of("act", "chapter", "article", "section", "subsection", "paragraph", "subparagraph", "title");
    private static final Pattern CROSSREF =
            Regex.ignoringCase("\\b(?:this|that|the\\s+(?:preceding|succeeding|following|same))\\s+(?:"
                    + String.join("|", UNITS) + ")s?\\b");

    @Override
    public String name() {
        return "cross-references";
    }

    @Override
    public List<Edit> edits(String text, CanonConfig config) {
        if (!config.normalizeCitations()) {
            return List.of();
        }
        List<Edit> edits = new ArrayList<>();
        Matcher match = CROSSREF.matcher(text);
        while (match.find()) {
            edits.add(new Edit(new TextSpan(match.start(), match.end()), SELF_REFERENCE));
        }
        return edits;
    }
}

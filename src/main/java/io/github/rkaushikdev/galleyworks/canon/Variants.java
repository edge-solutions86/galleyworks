package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spelling variants that mean the same thing.
 *
 * <p>Legislative drafting manuals disagree, and the same clause is re-typed with {@code judgement}
 * for {@code judgment} as it crosses a state line. Only pairs that are interchangeable in a statute
 * are listed: {@code insure} and {@code ensure} are different words in an insurance code and
 * folding them would erase a distinction the text is making on purpose.
 */
public final class Variants {

    /** Each variant spelling and the form it folds to. */
    public static final Map<String, String> VARIANTS = variants();

    private static final Pattern WORD = Regex.compile("[A-Za-z]+");

    private Variants() {}

    private static Map<String, String> variants() {
        String[][] pairs = {
            {"acknowledgement", "acknowledgment"},
            {"adviser", "advisor"},
            {"amongst", "among"},
            {"annulment", "annulment"},
            {"authorise", "authorize"},
            {"authorised", "authorized"},
            {"behaviour", "behavior"},
            {"cancelled", "canceled"},
            {"colour", "color"},
            {"defence", "defense"},
            {"enrolment", "enrollment"},
            {"favour", "favor"},
            {"fulfil", "fulfill"},
            {"instalment", "installment"},
            {"judgement", "judgment"},
            {"labour", "labor"},
            {"licence", "license"},
            {"neighbouring", "neighboring"},
            {"offence", "offense"},
            {"organisation", "organization"},
            {"practise", "practice"},
            {"programme", "program"},
            {"recognise", "recognize"},
            {"recognised", "recognized"},
            {"shew", "show"},
            {"travelling", "traveling"},
            {"whilst", "while"}
        };
        Map<String, String> table = new LinkedHashMap<>();
        for (String[] pair : pairs) {
            table.put(pair[0], pair[1]);
        }
        return Collections.unmodifiableMap(table);
    }

    /** The preferred form of {@code word}, or the word itself. */
    public static String canonicalSpelling(String word) {
        return VARIANTS.getOrDefault(Text.lower(word), word);
    }

    /** Folds interchangeable spellings to one form. */
    public static final class VariantSpellingRule implements Rule {

        @Override
        public String name() {
            return "variant-spelling";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.foldVariants()) {
                return List.of();
            }
            List<Edit> edits = new ArrayList<>();
            Matcher match = WORD.matcher(text);
            while (match.find()) {
                String word = match.group();
                String preferred = VARIANTS.get(Text.lower(word));
                if (preferred == null || preferred.equals(word)) {
                    continue;
                }
                edits.add(new Edit(new TextSpan(match.start(), match.end()), preferred));
            }
            return edits;
        }
    }
}

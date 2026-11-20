package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Function words, and the two places they should and should not be dropped.
 *
 * <p>Shingles keep stopwords: word order and the small words are exactly what distinguishes copied
 * drafting from parallel drafting, so the fingerprinting path never sees this list. SimHash
 * weighting is the other place, and there it matters a great deal, because an unweighted signature
 * is dominated by {@code the}, {@code of} and {@code shall}. The modal verbs are on the list:
 * {@link #isStopword} is advice about weight, never about meaning.
 */
public final class Stopwords {

    /** The function words. */
    public static final Set<String> STOPWORDS = Set.copyOf(Arrays.asList(("a an and any are as at be been being but by"
                    + " each for from has have if in into is it its"
                    + " may must no nor not of on or other otherwise"
                    + " per provided pursuant same shall should so subject such"
                    + " that the their them then there these this those to"
                    + " under upon was were which who whom whose will with within without")
            .split(" ")));

    private Stopwords() {}

    /** True when {@code token} is a function word, in any case. */
    public static boolean isStopword(String token) {
        return STOPWORDS.contains(Text.lower(token));
    }

    /** The tokens that carry subject matter, in order. */
    public static List<String> contentTokens(Collection<String> tokens) {
        return tokens.stream().filter(token -> !isStopword(token)).toList();
    }

    /**
     * Fraction of {@code tokens} that are not function words.
     *
     * <p>A segment under about a fifth content is a recital or a caption, and a match against one
     * says nothing about copying.
     */
    public static double contentRatio(Collection<String> tokens) {
        int total = 0;
        int content = 0;
        for (String token : tokens) {
            total++;
            if (!isStopword(token)) {
                content++;
            }
        }
        return total == 0 ? 0.0 : (double) content / total;
    }
}

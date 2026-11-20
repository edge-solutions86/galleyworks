package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The canonical rule order, and the named presets that switch parts of it off.
 *
 * <p>The order is load bearing: Unicode folding first, so every later pattern sees ASCII
 * punctuation; whitespace; defined terms while the quotation marks are still in place; citations
 * before numbers can read {@code 41-3-101} as three numerals; cross-references; numerals;
 * enumeration labels; the enacting clause; case folding, because the rules above match on
 * capitalisation; variant spellings on lowercase text; and morphology last, so a folded spelling
 * still loses its possessive. A profile never reorders rules, it only enables and disables them.
 */
public final class Profiles {

    /** The named profiles. */
    public static final Map<String, CanonConfig> PROFILES = profiles();

    private Profiles() {}

    private static Map<String, CanonConfig> profiles() {
        Map<String, CanonConfig> profiles = new LinkedHashMap<>();
        CanonConfig base = new CanonConfig();
        profiles.put("default", base);
        // Municipal codes renumber wholesale between adoptions, and their ordinance numbers are
        // not statutory citations, so citation folding is off.
        profiles.put("municipal", base.withProfile("municipal").withNormalizeCitations(false));
        // Federal drafting keeps its enacting clause short and does not repeat it, and the
        // doubled-numeral convention is rare.
        profiles.put(
                "federal",
                base.withProfile("federal").withDropEnactingClause(false).withNormalizeNumbers(false));
        // A verbatim profile for diffing two prints of the same act.
        profiles.put("verbatim", new CanonConfig("verbatim", false, true, false, false, false, false, false));
        return Collections.unmodifiableMap(profiles);
    }

    /** The canonical rule order. */
    public static List<Rule> defaultRules() {
        return List.of(
                new TextRules.UnicodeFoldRule(),
                new TextRules.WhitespaceRule(),
                new DefinedTerms.DefinedTermRule(),
                new Citations.CitationRule(),
                new CrossReferenceRule(),
                new Numerals.NumeralRule(),
                new StructureRules.EnumerationRule(),
                new StructureRules.EnactingClauseRule(),
                new TextRules.CaseFoldRule(),
                new Variants.VariantSpellingRule(),
                new Morphology.MorphologyRule());
    }

    /** A pipeline holding every rule; the config decides which ones act. */
    public static CanonPipeline buildPipeline() {
        return new CanonPipeline(defaultRules());
    }

    /** Look up a named profile. */
    public static CanonConfig profile(String name) {
        CanonConfig found = PROFILES.get(name);
        if (found == null) {
            throw new ConfigError(
                    "unknown canonicalization profile",
                    Context.of("profile", name, "known", String.join(",", new TreeSet<>(PROFILES.keySet()))));
        }
        return found;
    }

    /** Run the default pipeline over {@code text} under the default settings. */
    public static CanonResult canonicalize(String text) {
        return buildPipeline().run(text);
    }

    /** Run the default pipeline over {@code text} under {@code config}. */
    public static CanonResult canonicalize(String text, CanonConfig config) {
        return buildPipeline().run(text, config);
    }
}

package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which normalization rules run, and under which jurisdiction profile.
 *
 * @param profile the profile setting
 * @param foldCase the fold case setting
 * @param foldUnicode the fold unicode setting
 * @param foldVariants the fold variants setting
 * @param normalizeNumbers the normalize numbers setting
 * @param normalizeCitations the normalize citations setting
 * @param dropEnumerationLabels the drop enumeration labels setting
 * @param dropEnactingClause the drop enacting clause setting
 */
public record CanonConfig(
        String profile,
        boolean foldCase,
        boolean foldUnicode,
        boolean foldVariants,
        boolean normalizeNumbers,
        boolean normalizeCitations,
        boolean dropEnumerationLabels,
        boolean dropEnactingClause) {

    static final List<String> FIELDS = List.of(
            "profile",
            "fold_case",
            "fold_unicode",
            "fold_variants",
            "normalize_numbers",
            "normalize_citations",
            "drop_enumeration_labels",
            "drop_enacting_clause");

    /** Validates every field. */
    public CanonConfig {
        Objects.requireNonNull(profile, "profile");
    }

    /** Every field at its default. */
    public CanonConfig() {
        this("default", true, true, true, true, true, true, true);
    }

    /** This configuration with profile replaced. */
    public CanonConfig withProfile(String value) {
        return new CanonConfig(
                value,
                foldCase,
                foldUnicode,
                foldVariants,
                normalizeNumbers,
                normalizeCitations,
                dropEnumerationLabels,
                dropEnactingClause);
    }
    /** This configuration with foldCase replaced. */
    public CanonConfig withFoldCase(boolean value) {
        return new CanonConfig(
                profile,
                value,
                foldUnicode,
                foldVariants,
                normalizeNumbers,
                normalizeCitations,
                dropEnumerationLabels,
                dropEnactingClause);
    }
    /** This configuration with foldUnicode replaced. */
    public CanonConfig withFoldUnicode(boolean value) {
        return new CanonConfig(
                profile,
                foldCase,
                value,
                foldVariants,
                normalizeNumbers,
                normalizeCitations,
                dropEnumerationLabels,
                dropEnactingClause);
    }
    /** This configuration with foldVariants replaced. */
    public CanonConfig withFoldVariants(boolean value) {
        return new CanonConfig(
                profile,
                foldCase,
                foldUnicode,
                value,
                normalizeNumbers,
                normalizeCitations,
                dropEnumerationLabels,
                dropEnactingClause);
    }
    /** This configuration with normalizeNumbers replaced. */
    public CanonConfig withNormalizeNumbers(boolean value) {
        return new CanonConfig(
                profile,
                foldCase,
                foldUnicode,
                foldVariants,
                value,
                normalizeCitations,
                dropEnumerationLabels,
                dropEnactingClause);
    }
    /** This configuration with normalizeCitations replaced. */
    public CanonConfig withNormalizeCitations(boolean value) {
        return new CanonConfig(
                profile,
                foldCase,
                foldUnicode,
                foldVariants,
                normalizeNumbers,
                value,
                dropEnumerationLabels,
                dropEnactingClause);
    }
    /** This configuration with dropEnumerationLabels replaced. */
    public CanonConfig withDropEnumerationLabels(boolean value) {
        return new CanonConfig(
                profile,
                foldCase,
                foldUnicode,
                foldVariants,
                normalizeNumbers,
                normalizeCitations,
                value,
                dropEnactingClause);
    }
    /** This configuration with dropEnactingClause replaced. */
    public CanonConfig withDropEnactingClause(boolean value) {
        return new CanonConfig(
                profile,
                foldCase,
                foldUnicode,
                foldVariants,
                normalizeNumbers,
                normalizeCitations,
                dropEnumerationLabels,
                value);
    }

    /** The fields as a mapping, in declaration order, with plain values. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("profile", profile);
        mapping.put("fold_case", foldCase);
        mapping.put("fold_unicode", foldUnicode);
        mapping.put("fold_variants", foldVariants);
        mapping.put("normalize_numbers", normalizeNumbers);
        mapping.put("normalize_citations", normalizeCitations);
        mapping.put("drop_enumeration_labels", dropEnumerationLabels);
        mapping.put("drop_enacting_clause", dropEnactingClause);
        return mapping;
    }

    /** Read the {@code section} of a configuration mapping, recording problems. */
    static CanonConfig read(Object value, String section, List<String> problems) {
        CanonConfig defaults = new CanonConfig();
        ConfigFields fields = ConfigFields.of(value, section, java.util.Set.copyOf(FIELDS), problems);
        return fields.validated(
                "",
                defaults,
                () -> new CanonConfig(
                        fields.string("profile", defaults.profile()),
                        fields.flag("fold_case", defaults.foldCase()),
                        fields.flag("fold_unicode", defaults.foldUnicode()),
                        fields.flag("fold_variants", defaults.foldVariants()),
                        fields.flag("normalize_numbers", defaults.normalizeNumbers()),
                        fields.flag("normalize_citations", defaults.normalizeCitations()),
                        fields.flag("drop_enumeration_labels", defaults.dropEnumerationLabels()),
                        fields.flag("drop_enacting_clause", defaults.dropEnactingClause())));
    }
}

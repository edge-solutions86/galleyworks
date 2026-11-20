package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a report shows by default; command-line flags still override it.
 *
 * <p>These settings are not part of the index digest: changing what a report shows does not change
 * what the index holds, and forcing a reindex for a display change would be the wrong trade.
 *
 * @param minConfidence the min confidence setting
 * @param crossingOnly the crossing only setting
 * @param top the top setting
 * @param maxPassages the max passages setting
 */
public record ReportConfig(String minConfidence, boolean crossingOnly, Optional<Integer> top, int maxPassages) {

    static final List<String> FIELDS = List.of("min_confidence", "crossing_only", "top", "max_passages");

    /** Validates every field. */
    public ReportConfig {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(minConfidence, "min_confidence");
        ConfigChecks.oneOf("min_confidence", minConfidence, List.of("weak", "moderate", "strong"));
        top.ifPresent(value -> ConfigChecks.atLeast("top", value, 1));
        ConfigChecks.atLeast("max_passages", maxPassages, 1);
    }

    /** Every field at its default. */
    public ReportConfig() {
        this("weak", false, Optional.empty(), 3);
    }

    /** This configuration with minConfidence replaced. */
    public ReportConfig withMinConfidence(String value) {
        return new ReportConfig(value, crossingOnly, top, maxPassages);
    }
    /** This configuration with crossingOnly replaced. */
    public ReportConfig withCrossingOnly(boolean value) {
        return new ReportConfig(minConfidence, value, top, maxPassages);
    }
    /** This configuration with top replaced. */
    public ReportConfig withTop(Optional<Integer> value) {
        return new ReportConfig(minConfidence, crossingOnly, value, maxPassages);
    }
    /** This configuration with maxPassages replaced. */
    public ReportConfig withMaxPassages(int value) {
        return new ReportConfig(minConfidence, crossingOnly, top, value);
    }

    /** The fields as a mapping, in declaration order, with plain values. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("min_confidence", minConfidence);
        mapping.put("crossing_only", crossingOnly);
        mapping.put("top", top.orElse(null));
        mapping.put("max_passages", maxPassages);
        return mapping;
    }

    /** Read the {@code section} of a configuration mapping, recording problems. */
    static ReportConfig read(Object value, String section, List<String> problems) {
        ReportConfig defaults = new ReportConfig();
        ConfigFields fields = ConfigFields.of(value, section, java.util.Set.copyOf(FIELDS), problems);
        return fields.validated(
                "",
                defaults,
                () -> new ReportConfig(
                        fields.choice(
                                "min_confidence", defaults.minConfidence(), List.of("weak", "moderate", "strong")),
                        fields.flag("crossing_only", defaults.crossingOnly()),
                        fields.optionalInteger("top", defaults.top()),
                        fields.integer("max_passages", defaults.maxPassages())));
    }
}

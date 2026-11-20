package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How documents are read before anything looks at their words.
 *
 * <p>An amendment draft describes two texts and the reader has to pick one. A drafting office
 * checking a bill against enacted law wants {@code current}; a researcher tracking where language
 * is going wants {@code proposed}. It is a corpus-level decision, so it lives in the configuration
 * and is part of the index digest: a corpus half read one way and half the other is not comparable
 * with itself.
 *
 * @param amendmentReading the amendment reading setting
 * @param defaultChamber the default chamber setting
 */
public record IngestConfig(String amendmentReading, String defaultChamber) {

    static final List<String> FIELDS = List.of("amendment_reading", "default_chamber");

    /** Validates every field. */
    public IngestConfig {
        Objects.requireNonNull(defaultChamber, "default_chamber");
        Objects.requireNonNull(amendmentReading, "amendment_reading");
        ConfigChecks.oneOf("amendment_reading", amendmentReading, List.of("current", "proposed"));
    }

    /** Every field at its default. */
    public IngestConfig() {
        this("proposed", "lower");
    }

    /** This configuration with amendmentReading replaced. */
    public IngestConfig withAmendmentReading(String value) {
        return new IngestConfig(value, defaultChamber);
    }
    /** This configuration with defaultChamber replaced. */
    public IngestConfig withDefaultChamber(String value) {
        return new IngestConfig(amendmentReading, value);
    }

    /** The fields as a mapping, in declaration order, with plain values. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("amendment_reading", amendmentReading);
        mapping.put("default_chamber", defaultChamber);
        return mapping;
    }

    /** Read the {@code section} of a configuration mapping, recording problems. */
    static IngestConfig read(Object value, String section, List<String> problems) {
        IngestConfig defaults = new IngestConfig();
        ConfigFields fields = ConfigFields.of(value, section, java.util.Set.copyOf(FIELDS), problems);
        return fields.validated(
                "",
                defaults,
                () -> new IngestConfig(
                        fields.choice("amendment_reading", defaults.amendmentReading(), List.of("current", "proposed")),
                        fields.string("default_chamber", defaults.defaultChamber())));
    }
}

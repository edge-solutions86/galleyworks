package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MinHash signature width and the banding used to bucket it.
 *
 * @param permutations the permutations setting
 * @param bands the bands setting
 * @param seed the seed setting
 */
public record LshConfig(int permutations, int bands, long seed) {

    static final List<String> FIELDS = List.of("permutations", "bands", "seed");

    /** Validates every field. */
    public LshConfig {
        ConfigChecks.atLeast("permutations", permutations, 8);
        ConfigChecks.atMost("permutations", permutations, 1024);
        ConfigChecks.atLeast("bands", bands, 1);
        ConfigChecks.atMost("bands", bands, 512);
        ConfigChecks.atLeast("seed", seed, 0);
        if (permutations % bands != 0) {
            throw new IllegalArgumentException(
                    "Value error, bands=" + bands + " does not divide permutations=" + permutations);
        }
    }

    /** Every field at its default. */
    public LshConfig() {
        this(128, 32, 0x5EED);
    }

    /** Signature rows per band. */
    public int rows() {
        return permutations / bands;
    }

    /** The Jaccard similarity at which this banding is half likely to fire. */
    public double approximateThreshold() {
        return Math.pow(1.0 / bands, 1.0 / rows());
    }

    /** This configuration with permutations replaced. */
    public LshConfig withPermutations(int value) {
        return new LshConfig(value, bands, seed);
    }
    /** This configuration with bands replaced. */
    public LshConfig withBands(int value) {
        return new LshConfig(permutations, value, seed);
    }
    /** This configuration with seed replaced. */
    public LshConfig withSeed(long value) {
        return new LshConfig(permutations, bands, value);
    }

    /** The fields as a mapping, in declaration order, with plain values. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("permutations", permutations);
        mapping.put("bands", bands);
        mapping.put("seed", seed);
        return mapping;
    }

    /** Read the {@code section} of a configuration mapping, recording problems. */
    static LshConfig read(Object value, String section, List<String> problems) {
        LshConfig defaults = new LshConfig();
        ConfigFields fields = ConfigFields.of(value, section, java.util.Set.copyOf(FIELDS), problems);
        return fields.validated(
                "",
                defaults,
                () -> new LshConfig(
                        fields.integer("permutations", defaults.permutations()),
                        fields.integer("bands", defaults.bands()),
                        fields.longInteger("seed", defaults.seed())));
    }
}

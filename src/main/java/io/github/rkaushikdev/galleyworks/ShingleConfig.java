package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How the canonical token stream is cut into overlapping windows.
 *
 * @param mode the mode setting
 * @param width the width setting
 * @param winnowWindow the winnow window setting
 */
public record ShingleConfig(ShingleMode mode, int width, int winnowWindow) {

    static final List<String> FIELDS = List.of("mode", "width", "winnow_window");

    /** Validates every field. */
    public ShingleConfig {
        Objects.requireNonNull(mode, "mode");
        ConfigChecks.atLeast("width", width, 1);
        ConfigChecks.atMost("width", width, 64);
        ConfigChecks.atLeast("winnow_window", winnowWindow, 1);
        ConfigChecks.atMost("winnow_window", winnowWindow, 64);
    }

    /** Every field at its default. */
    public ShingleConfig() {
        this(ShingleMode.WORD, 5, 4);
    }

    /** Shortest passage the winnowing scheme is guaranteed to detect. */
    public int guaranteeLength() {
        return width + winnowWindow - 1;
    }

    /** This configuration with mode replaced. */
    public ShingleConfig withMode(ShingleMode value) {
        return new ShingleConfig(value, width, winnowWindow);
    }
    /** This configuration with width replaced. */
    public ShingleConfig withWidth(int value) {
        return new ShingleConfig(mode, value, winnowWindow);
    }
    /** This configuration with winnowWindow replaced. */
    public ShingleConfig withWinnowWindow(int value) {
        return new ShingleConfig(mode, width, value);
    }

    /** The fields as a mapping, in declaration order, with plain values. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("mode", mode.value());
        mapping.put("width", width);
        mapping.put("winnow_window", winnowWindow);
        return mapping;
    }

    /** Read the {@code section} of a configuration mapping, recording problems. */
    static ShingleConfig read(Object value, String section, List<String> problems) {
        ShingleConfig defaults = new ShingleConfig();
        ConfigFields fields = ConfigFields.of(value, section, java.util.Set.copyOf(FIELDS), problems);
        return fields.validated(
                "",
                defaults,
                () -> new ShingleConfig(
                        ShingleMode.read(fields, "mode", defaults.mode()),
                        fields.integer("width", defaults.width()),
                        fields.integer("winnow_window", defaults.winnowWindow())));
    }
}

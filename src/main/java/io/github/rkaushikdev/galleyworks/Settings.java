package io.github.rkaushikdev.galleyworks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Loads an {@link EngineConfig} from a YAML file and the environment.
 *
 * <p>Precedence, lowest first: packaged defaults, the YAML file, then environment variables. An
 * operator can therefore keep a checked-in profile and still retune one threshold on a single box
 * without editing it. Environment keys look like {@code LEXDRIFT__MATCH__MIN_CONTAINMENT=0.5}: the
 * double underscore is the nesting separator, because single underscores already appear inside
 * field names.
 */
public final class Settings {

    /** The prefix every override variable carries. */
    public static final String ENV_PREFIX = "LEXDRIFT__";

    /** The separator between nesting levels in an override variable. */
    public static final String ENV_SEPARATOR = "__";

    private Settings() {}

    /** Turn an environment string into the closest YAML scalar, or keep it when it is not YAML. */
    public static Object parseScalar(String raw) {
        try {
            return yaml().load(raw);
        } catch (YAMLException exc) {
            return raw;
        }
    }

    /** Recursively merge {@code overlay} onto {@code base} without mutating either. */
    public static Map<String, Object> deepMerge(Map<String, ?> base, Map<String, ?> overlay) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        overlay.forEach((key, value) -> {
            Object existing = merged.get(key);
            if (existing instanceof Map<?, ?> left && value instanceof Map<?, ?> right) {
                merged.put(key, deepMerge(stringKeys(left), stringKeys(right)));
            } else {
                merged.put(key, value);
            }
        });
        return merged;
    }

    /** Fold the {@code LEXDRIFT__} variables of the process environment into {@code data}. */
    public static Map<String, Object> applyEnvOverrides(Map<String, ?> data) {
        return applyEnvOverrides(data, System.getenv());
    }

    /** Fold the {@code LEXDRIFT__} variables of {@code environ} into {@code data}. */
    public static Map<String, Object> applyEnvOverrides(Map<String, ?> data, Map<String, String> environ) {
        Map<String, Object> overlay = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(environ).entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(ENV_PREFIX)) {
                continue;
            }
            List<String> path = new ArrayList<>();
            for (String part : name.substring(ENV_PREFIX.length()).split(ENV_SEPARATOR, -1)) {
                if (!part.isEmpty()) {
                    path.add(part.toLowerCase(Locale.ROOT));
                }
            }
            if (path.isEmpty()) {
                throw new ConfigError("environment override has no field path", Context.of("variable", name));
            }
            Map<String, Object> cursor = overlay;
            for (String part : path.subList(0, path.size() - 1)) {
                Object branch = cursor.computeIfAbsent(part, ignored -> new LinkedHashMap<String, Object>());
                if (!(branch instanceof Map<?, ?>)) {
                    throw new ConfigError("environment override collides with a scalar", Context.of("variable", name));
                }
                cursor = castBranch(branch);
            }
            cursor.put(path.getLast(), parseScalar(entry.getValue()));
        }
        return deepMerge(data, overlay);
    }

    /** Defaults plus the process environment. */
    public static EngineConfig loadConfig() {
        return loadConfig(Optional.empty(), System.getenv());
    }

    /** {@code path} plus the process environment. */
    public static EngineConfig loadConfig(Path path) {
        return loadConfig(Optional.of(path), System.getenv());
    }

    /** An optional YAML file plus the overrides in {@code environ}. */
    public static EngineConfig loadConfig(Optional<Path> path, Map<String, String> environ) {
        Map<String, Object> data = path.map(Settings::readYaml).orElseGet(LinkedHashMap::new);
        return EngineConfig.fromMapping(applyEnvOverrides(data, environ));
    }

    private static Map<String, Object> readYaml(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exc) {
            throw new ConfigError("configuration file cannot be read", Context.of("path", path.toString()), exc);
        }
        Object loaded;
        try {
            loaded = yaml().load(text);
        } catch (YAMLException exc) {
            throw new ConfigError("configuration file is not valid YAML", Context.of("path", path.toString()), exc);
        }
        if (loaded == null) {
            return new LinkedHashMap<>();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new ConfigError("configuration file must hold a mapping", Context.of("path", path.toString()));
        }
        return stringKeys(map);
    }

    /** A YAML loader that builds only plain values. */
    public static Yaml yaml() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private static Map<String, Object> stringKeys(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    @SuppressWarnings("unchecked") // every branch in the overlay is created above as Map<String, Object>
    private static Map<String, Object> castBranch(Object branch) {
        return (Map<String, Object>) branch;
    }
}

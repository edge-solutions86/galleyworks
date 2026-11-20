package io.github.rkaushikdev.galleyworks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Builds the ordered, null-tolerant context maps that errors carry. */
public final class Context {

    private Context() {}

    /** A context from alternating keys and values, kept in the order given. */
    public static Map<String, Object> of(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("context needs a value for every key");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            context.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(context);
    }
}

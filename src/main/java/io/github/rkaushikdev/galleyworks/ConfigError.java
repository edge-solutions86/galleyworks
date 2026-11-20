package io.github.rkaushikdev.galleyworks;

import java.util.Map;

/** The engine was handed a configuration it cannot run with. */
public class ConfigError extends LexdriftError {

    /** An error with no context. */
    public ConfigError(String message) {
        super(message);
    }

    /** An error carrying {@code context}, rendered after the message. */
    public ConfigError(String message, Map<String, ?> context) {
        super(message, context);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public ConfigError(String message, Map<String, ?> context, Throwable cause) {
        super(message, context, cause);
    }

    @Override
    public String code() {
        return "lexdrift.config";
    }

    @Override
    public int httpStatus() {
        return 400;
    }
}

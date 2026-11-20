package io.github.rkaushikdev.galleyworks;

import java.util.Map;

/** The index store rejected a read or a write. */
public class StoreError extends LexdriftError {

    /** An error with no context. */
    public StoreError(String message) {
        super(message);
    }

    /** An error carrying {@code context}, rendered after the message. */
    public StoreError(String message, Map<String, ?> context) {
        super(message, context);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public StoreError(String message, Map<String, ?> context, Throwable cause) {
        super(message, context, cause);
    }

    @Override
    public String code() {
        return "lexdrift.store";
    }

    @Override
    public int httpStatus() {
        return 503;
    }
}

package io.github.rkaushikdev.galleyworks;

import java.util.Map;

/** A canonicalization rule could not be applied. */
public class CanonError extends LexdriftError {

    /** An error with no context. */
    public CanonError(String message) {
        super(message);
    }

    /** An error carrying {@code context}, rendered after the message. */
    public CanonError(String message, Map<String, ?> context) {
        super(message, context);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public CanonError(String message, Map<String, ?> context, Throwable cause) {
        super(message, context, cause);
    }

    @Override
    public String code() {
        return "lexdrift.canon";
    }
}

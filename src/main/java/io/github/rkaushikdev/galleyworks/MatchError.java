package io.github.rkaushikdev.galleyworks;

import java.util.Map;

/** Candidate generation or alignment failed. */
public class MatchError extends LexdriftError {

    /** An error with no context. */
    public MatchError(String message) {
        super(message);
    }

    /** An error carrying {@code context}, rendered after the message. */
    public MatchError(String message, Map<String, ?> context) {
        super(message, context);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public MatchError(String message, Map<String, ?> context, Throwable cause) {
        super(message, context, cause);
    }

    @Override
    public String code() {
        return "lexdrift.match";
    }
}

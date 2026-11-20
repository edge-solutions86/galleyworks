package io.github.rkaushikdev.galleyworks;

import java.util.Map;

/** A source document could not be turned into an instrument. */
public class IngestError extends LexdriftError {

    /** An error with no context. */
    public IngestError(String message) {
        super(message);
    }

    /** An error carrying {@code context}, rendered after the message. */
    public IngestError(String message, Map<String, ?> context) {
        super(message, context);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public IngestError(String message, Map<String, ?> context, Throwable cause) {
        super(message, context, cause);
    }

    @Override
    public String code() {
        return "lexdrift.ingest";
    }

    @Override
    public int httpStatus() {
        return 422;
    }
}

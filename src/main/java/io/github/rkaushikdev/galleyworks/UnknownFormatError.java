package io.github.rkaushikdev.galleyworks;

import java.util.Map;

/** No registered reader claimed the document. */
public class UnknownFormatError extends IngestError {

    /** An error with no context. */
    public UnknownFormatError(String message) {
        super(message);
    }

    /** An error carrying {@code context}, rendered after the message. */
    public UnknownFormatError(String message, Map<String, ?> context) {
        super(message, context);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public UnknownFormatError(String message, Map<String, ?> context, Throwable cause) {
        super(message, context, cause);
    }

    @Override
    public String code() {
        return "lexdrift.ingest.format";
    }

    @Override
    public int httpStatus() {
        return 415;
    }
}

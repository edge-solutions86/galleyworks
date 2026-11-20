package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.util.Repr;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Base class for everything the engine raises deliberately.
 *
 * <p>Every failure that can reach a caller carries a stable {@link #code()}. The codes are part of
 * the CLI and service contract, so they change only on a major version. Each type also carries the
 * HTTP status it means: an error knows whether it is the caller's fault, and the transport should
 * not have to be told.
 *
 * <p>{@link #message()} is the bare message; {@link #getMessage()} renders the context after it,
 * sorted by key, as in {@code bad profile (profile='wy-2024', stage='load')}.
 */
public class LexdriftError extends RuntimeException {

    private final String message;
    private final Map<String, Object> context;

    /** An error with no context. */
    public LexdriftError(String message) {
        this(message, Map.of());
    }

    /** An error carrying {@code context}, rendered after the message. */
    public LexdriftError(String message, Map<String, ?> context) {
        this(message, context, null);
    }

    /** An error carrying {@code context}, caused by {@code cause}. */
    public LexdriftError(String message, Map<String, ?> context, Throwable cause) {
        super(message, cause);
        this.message = message;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    /** The stable code that names this failure in the CLI and the service. */
    public String code() {
        return "lexdrift.error";
    }

    /** The HTTP status this failure means. */
    public int httpStatus() {
        return 500;
    }

    /** The message without its context. */
    public String message() {
        return message;
    }

    /** The context the error was raised with, in the order it was given. */
    public Map<String, Object> context() {
        return context;
    }

    /** The error as the service writes it: code, message and, when present, context. */
    public Map<String, Object> toDict() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code());
        payload.put("message", message);
        if (!context.isEmpty()) {
            payload.put("context", new LinkedHashMap<>(context));
        }
        return payload;
    }

    @Override
    public String getMessage() {
        if (context.isEmpty()) {
            return message;
        }
        Map<String, Object> sorted = new TreeMap<>(context);
        String detail = sorted.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + Repr.of(entry.getValue()))
                .collect(Collectors.joining(", "));
        return message + " (" + detail + ")";
    }

    /** The HTTP status {@code error} means, or 500 for anything foreign. */
    public static int httpStatusOf(Throwable error) {
        return error instanceof LexdriftError known ? known.httpStatus() : 500;
    }

    /** The stable code for {@code error}, or the generic code for foreign errors. */
    public static String errorCodeOf(Throwable error) {
        return error instanceof LexdriftError known ? known.code() : "lexdrift.unexpected";
    }
}

package io.github.rkaushikdev.galleyworks.service;

import io.github.rkaushikdev.galleyworks.util.Text;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * The bearer token the service expects, if any. With no token configured every request is
 * accepted.
 *
 * @param token the expected token
 */
public record TokenPolicy(Optional<String> token) {

    /** The environment variable the expected token is read from. */
    public static final String TOKEN_ENV = "LEXDRIFT_API_TOKEN";

    private static final String SCHEME = "bearer";

    /** A policy that accepts every request. */
    public static TokenPolicy open() {
        return new TokenPolicy(Optional.empty());
    }

    /** A policy that requires {@code token}. */
    public static TokenPolicy requiring(String token) {
        return new TokenPolicy(Optional.of(token).filter(value -> !value.isEmpty()));
    }

    /** The policy {@code environ} configures through {@value #TOKEN_ENV}. */
    public static TokenPolicy fromEnvironment(Map<String, String> environ) {
        String token = Text.strip(environ.getOrDefault(TOKEN_ENV, ""));
        return token.isEmpty() ? open() : requiring(token);
    }

    /** True when a token is required. */
    public boolean isEnforced() {
        return token.isPresent();
    }

    /** True when {@code presented} gets a request through, compared in constant time. */
    public boolean accepts(Optional<String> presented) {
        if (!isEnforced()) {
            return true;
        }
        return presented
                .map(value -> MessageDigest.isEqual(
                        value.getBytes(StandardCharsets.UTF_8), token.get().getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    /** The token in an {@code Authorization} header, when it carries a bearer token. */
    public static Optional<String> bearerToken(Optional<String> header) {
        if (header.isEmpty() || header.get().isEmpty()) {
            return Optional.empty();
        }
        String value = header.get();
        int space = value.indexOf(' ');
        String scheme = space < 0 ? value : value.substring(0, space);
        String rest = space < 0 ? "" : Text.strip(value.substring(space + 1));
        if (!Text.lower(scheme).equals(SCHEME) || rest.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rest);
    }
}

package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.TextSpan;

/**
 * One rewrite a canonicalization rule asks for: replace {@code span} with {@code replacement}.
 *
 * @param span the characters to replace
 * @param replacement what takes their place, possibly nothing
 */
public record Edit(TextSpan span, String replacement) {

    /** True when the edit removes text without replacing it. */
    public boolean isDeletion() {
        return replacement.isEmpty();
    }

    /** How much longer the text gets, negative when it shrinks. */
    public int delta() {
        return replacement.length() - span.length();
    }
}

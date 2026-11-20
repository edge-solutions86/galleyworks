package io.github.rkaushikdev.galleyworks.util;

/**
 * One aligned run of an {@link OffsetMap}: {@code target} in the rewritten text corresponds to
 * {@code source} in the original.
 *
 * @param source the run in the original text
 * @param target the run in the rewritten text
 */
public record Piece(TextSpan source, TextSpan target) {

    /** True when the run was copied through unchanged in length. */
    public boolean isAligned() {
        return source.length() == target.length() && source.length() > 0;
    }
}

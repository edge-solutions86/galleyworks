package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.OffsetMap;

/**
 * Text after a set of edits, with the map back to the text before them.
 *
 * @param text the rewritten text
 * @param mapping translates rewritten offsets back to the text before the rewrite
 */
public record Rewrite(String text, OffsetMap mapping) {}

package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.List;

/**
 * A canonicalization rule. A rule reports edits; it never rewrites text itself, which is what lets
 * the pipeline keep an offset map and reject two rules that disagree about the same characters.
 */
public interface Rule {

    /** The name the pipeline records when this rule changes something. */
    String name();

    /** The edits this rule wants applied to {@code text}. */
    List<Edit> edits(String text, CanonConfig config);
}

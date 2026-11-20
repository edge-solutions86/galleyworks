package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.List;

/**
 * Anything that can canonicalize text. The plain pipeline and the caching wrapper both are, which
 * lets the fingerprinter take either without knowing which it has.
 */
public interface Canonicalizer {

    /** The rules applied, in order. */
    List<String> ruleNames();

    /** Canonicalize {@code text} under {@code config}. */
    CanonResult run(String text, CanonConfig config);

    /** Canonicalize {@code text} under the default settings. */
    default CanonResult run(String text) {
        return run(text, new CanonConfig());
    }
}

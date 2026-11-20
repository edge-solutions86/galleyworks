package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.util.Optional;

/**
 * Supplies the raw text of an indexed instrument, so a report can quote the other side of a match.
 * The matcher never holds the other document, so the caller provides one of these.
 */
public interface TextSource {

    /** The raw text of {@code key}, when it is available. */
    Optional<String> textOf(InstrumentKey key);
}

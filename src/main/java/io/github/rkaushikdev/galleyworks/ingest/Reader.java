package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Instrument;

/**
 * A format-specific document reader. Sniffing is separate from reading so the registry can pick a
 * winner without parsing anything.
 */
public interface Reader {

    /** The name the registry and the instrument's source format record. */
    String name();

    /** Confidence in {@code [0, 1]} that this reader understands the document. */
    double sniff(SourceDocument document);

    /**
     * Parse the document into an instrument.
     *
     * @throws io.github.rkaushikdev.galleyworks.IngestError when the document cannot be read
     */
    Instrument read(SourceDocument document);
}

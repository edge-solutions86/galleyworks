package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.ingest.Markup.MarkedText;
import io.github.rkaushikdev.galleyworks.util.OffsetMap;

/**
 * Reads an amendment draft under one reading of its markup.
 *
 * <p>The reader picks one reading, parses it with the ordinary enrolled parser, then maps every
 * segment span back onto the markup. The instrument therefore keeps the marked-up text as its raw
 * text, which is what an analyst wants to see quoted, while the structure reflects the reading that
 * was selected.
 */
public final class AmendmentReader implements Reader {

    private final Reading reading;
    private final EnrolledReader inner = new EnrolledReader();

    /** A reader taking the proposed reading. */
    public AmendmentReader() {
        this(Reading.PROPOSED);
    }

    /** A reader taking {@code reading}. */
    public AmendmentReader(Reading reading) {
        this.reading = reading;
    }

    /** The reading this reader selects. */
    public Reading reading() {
        return reading;
    }

    @Override
    public String name() {
        return "amendment-" + reading.value();
    }

    @Override
    public double sniff(SourceDocument document) {
        if (!Markup.hasMarkup(document.text())) {
            return 0.0;
        }
        String selected = select(Markup.parseMarkup(document.text())).text();
        double score = inner.sniff(document.withText(selected));
        if (score == 0.0) {
            return 0.0;
        }
        return Math.min(1.0, score + 0.4);
    }

    @Override
    public Instrument read(SourceDocument document) {
        Selected body = select(Markup.parseMarkup(document.text()));
        Instrument parsed = inner.read(document.withText(body.text()));
        return new Instrument(
                parsed.key(),
                parsed.title(),
                document.text(),
                parsed.segments().stream()
                        .map(segment -> remap(segment, body.mapping()))
                        .toList(),
                parsed.introducedOn(),
                parsed.enactedOn(),
                name());
    }

    private record Selected(String text, OffsetMap mapping) {}

    private Selected select(MarkedText parsed) {
        if (reading == Reading.CURRENT) {
            return new Selected(parsed.current(), parsed.currentMap());
        }
        return new Selected(parsed.proposed(), parsed.proposedMap());
    }

    private static Segment remap(Segment segment, OffsetMap mapping) {
        return new Segment(
                segment.index(),
                segment.kind(),
                segment.path(),
                mapping.toSourceSpan(segment.span()),
                segment.heading(),
                segment.parent());
    }
}

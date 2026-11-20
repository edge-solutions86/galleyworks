package io.github.rkaushikdev.galleyworks;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A single legislative document plus its parsed structure.
 *
 * <p>The instrument owns its raw text; segments only carry spans into it.
 *
 * @param key the identity the corpus knows it by
 * @param title the short title
 * @param rawText the text exactly as printed
 * @param segments the parsed structure, in document order
 * @param introducedOn when it was introduced, if known
 * @param enactedOn when it was enacted, if known
 * @param sourceFormat the name of the reader that produced it
 */
public record Instrument(
        InstrumentKey key,
        String title,
        String rawText,
        List<Segment> segments,
        Optional<LocalDate> introducedOn,
        Optional<LocalDate> enactedOn,
        String sourceFormat) {

    /** Copies the segments and checks every span lies inside the text. */
    public Instrument {
        segments = List.copyOf(segments);
        int limit = rawText.length();
        for (Segment segment : segments) {
            if (segment.span().end() > limit) {
                throw new IngestError(
                        "segment span runs past the end of the document",
                        Context.of(
                                "index", segment.index(), "end", segment.span().end(), "length", limit));
            }
        }
    }

    /** An instrument of unknown format with no dates. */
    public Instrument(InstrumentKey key, String title, String rawText, List<Segment> segments) {
        this(key, title, rawText, segments, Optional.empty(), Optional.empty(), "unknown");
    }

    /** A builder starting from the three fields every instrument has. */
    public static Builder builder(InstrumentKey key, String title, String rawText) {
        return new Builder(key, title, rawText);
    }

    /** The same instrument with different raw text; the segments are kept. */
    public Instrument withRawText(String replacement) {
        return new Instrument(key, title, replacement, segments, introducedOn, enactedOn, sourceFormat);
    }

    /** The date the corpus should sort this instrument by: enactment, else introduction. */
    public Optional<LocalDate> effectiveDate() {
        return enactedOn.or(() -> introducedOn);
    }

    /** The segments that carry enacted substance, in order. */
    public List<Segment> bodySegments() {
        return segments.stream().filter(segment -> segment.kind().isBody()).toList();
    }

    /** The segment whose index is {@code index}. */
    public Segment segmentAt(int index) {
        for (Segment segment : segments) {
            if (segment.index() == index) {
                return segment;
            }
        }
        throw new IngestError("no such segment", Context.of("index", index, "key", key.slug()));
    }

    /** The segments directly under {@code index}. */
    public List<Segment> childrenOf(int index) {
        return segments.stream()
                .filter(segment ->
                        segment.parent().isPresent() && segment.parent().getAsInt() == index)
                .toList();
    }

    /** The text of each of {@code chosen}, in order. */
    public List<String> textFor(List<Segment> chosen) {
        return chosen.stream().map(segment -> segment.span().sliceOf(rawText)).toList();
    }

    /** Assembles an {@link Instrument} field by field. */
    public static final class Builder {

        private final InstrumentKey key;
        private final String title;
        private final String rawText;
        private List<Segment> segments = List.of();
        private Optional<LocalDate> introducedOn = Optional.empty();
        private Optional<LocalDate> enactedOn = Optional.empty();
        private String sourceFormat = "unknown";

        private Builder(InstrumentKey key, String title, String rawText) {
            this.key = key;
            this.title = title;
            this.rawText = rawText;
        }

        /** The parsed structure. */
        public Builder segments(List<Segment> value) {
            this.segments = value;
            return this;
        }

        /** When it was introduced. */
        public Builder introducedOn(LocalDate value) {
            this.introducedOn = Optional.ofNullable(value);
            return this;
        }

        /** When it was enacted. */
        public Builder enactedOn(LocalDate value) {
            this.enactedOn = Optional.ofNullable(value);
            return this;
        }

        /** The reader that produced it. */
        public Builder sourceFormat(String value) {
            this.sourceFormat = value;
            return this;
        }

        /** The instrument. */
        public Instrument build() {
            return new Instrument(key, title, rawText, segments, introducedOn, enactedOn, sourceFormat);
        }
    }
}

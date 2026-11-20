package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.util.List;
import java.util.Optional;

/**
 * Everything the index holds about one segment: the winnowed fingerprints with spans in the raw
 * text, a MinHash signature, a SimHash and the token count.
 *
 * @param key the instrument the segment belongs to
 * @param segmentIndex the segment's index in that instrument
 * @param path the printed label chain
 * @param tokenCount words and numbers in the canonical text
 * @param signature the MinHash signature of the selected fingerprints
 * @param simhash the weighted SimHash of the canonical tokens
 * @param selected the winnowed shingles, with spans in the raw text
 * @param canonical the canonical text, empty once a record has been through the index
 * @param contentRatio the share of tokens that are not function words
 */
public record SegmentFingerprint(
        InstrumentKey key,
        int segmentIndex,
        String path,
        int tokenCount,
        Signature signature,
        long simhash,
        List<Shingle> selected,
        String canonical,
        double contentRatio) {

    private static final double RECITAL_CONTENT = 0.25;

    /** Copies the selection. */
    public SegmentFingerprint {
        selected = List.copyOf(selected);
    }

    /** A record whose content ratio is not known, which counts as all content. */
    public SegmentFingerprint(
            InstrumentKey key,
            int segmentIndex,
            String path,
            int tokenCount,
            Signature signature,
            long simhash,
            List<Shingle> selected,
            String canonical) {
        this(key, segmentIndex, path, tokenCount, signature, simhash, selected, canonical, 1.0);
    }

    /**
     * True when the segment is mostly function words.
     *
     * <p>A recital or a caption matches everything and means nothing, so the matcher is allowed to
     * skip it before doing any set arithmetic.
     */
    public boolean isRecital() {
        return contentRatio < RECITAL_CONTENT;
    }

    /** True when the segment has no words at all. */
    public boolean isEmpty() {
        return tokenCount == 0;
    }

    /** The hash of every selected shingle, in order. */
    public List<Long> fingerprintValues() {
        return selected.stream().map(Shingle::value).toList();
    }

    /** The first selected shingle with hash {@code value}. */
    public Optional<Shingle> spanOf(long value) {
        return selected.stream().filter(shingle -> shingle.value() == value).findFirst();
    }

    /** This record without its canonical text, which the codec never writes. */
    public SegmentFingerprint withoutCanonical() {
        return new SegmentFingerprint(
                key, segmentIndex, path, tokenCount, signature, simhash, selected, "", contentRatio);
    }
}

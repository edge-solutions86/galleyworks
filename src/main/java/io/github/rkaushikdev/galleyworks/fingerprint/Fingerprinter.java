package io.github.rkaushikdev.galleyworks.fingerprint;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.canon.CachingPipeline;
import io.github.rkaushikdev.galleyworks.canon.CanonResult;
import io.github.rkaushikdev.galleyworks.canon.Canonicalizer;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.canon.Stopwords;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import java.util.List;

/**
 * Turns a parsed instrument into the records the index stores, one per body segment, under one
 * engine configuration.
 *
 * <p>Spans are translated back through the canonicalization offset map here, once, so no later
 * stage has to know that canonical text ever existed.
 */
public final class Fingerprinter {

    private static final double STOPWORD_WEIGHT = 0.2;

    private final EngineConfig config;
    private final Canonicalizer pipeline;
    private final MinHashSigner signer;

    /** A fingerprinter under the default configuration. */
    public Fingerprinter() {
        this(new EngineConfig());
    }

    /**
     * A fingerprinter under {@code config} with a cached pipeline: a corpus re-prints the same
     * section in every bill that amends it.
     */
    public Fingerprinter(EngineConfig config) {
        this(config, new CachingPipeline(Profiles.buildPipeline()));
    }

    /** A fingerprinter under {@code config} canonicalizing through {@code pipeline}. */
    public Fingerprinter(EngineConfig config, Canonicalizer pipeline) {
        this.config = config;
        this.pipeline = pipeline;
        this.signer = new MinHashSigner(config.lsh());
    }

    /** The configuration records are built under. */
    public EngineConfig config() {
        return config;
    }

    /** The canonicalizer in use. */
    public Canonicalizer pipeline() {
        return pipeline;
    }

    /** The digest of the configuration. */
    public String digest() {
        return config.digest();
    }

    /** Fingerprint every body segment of {@code instrument} that has any words. */
    public List<SegmentFingerprint> fingerprint(Instrument instrument) {
        return instrument.bodySegments().stream()
                .map(segment -> fingerprintSegment(instrument, segment))
                .filter(record -> !record.isEmpty())
                .toList();
    }

    /** Fingerprint one segment of {@code instrument}. */
    public SegmentFingerprint fingerprintSegment(Instrument instrument, Segment segment) {
        String raw = segment.span().sliceOf(instrument.rawText());
        CanonResult canon = pipeline.run(raw, config.canon());
        List<String> tokens = Tokens.wordsOnly(Tokens.tokenize(canon.text())).stream()
                .map(Tokens.Token::lowered)
                .toList();
        List<Shingle> shingles = Shingles.shinglesFor(canon.text(), config.shingle());
        List<Shingle> selected = Winnow.winnow(shingles, config.shingle().winnowWindow());
        int base = segment.span().start();
        List<Shingle> located = selected.stream()
                .map(shingle -> shingle.withSpan(canon.toSource(shingle.span()).shift(base)))
                .toList();
        return new SegmentFingerprint(
                instrument.key(),
                segment.index(),
                segment.path().printed(),
                tokens.size(),
                signer.sign(selected.stream().mapToLong(Shingle::value).toArray()),
                SimHash.simhash(weighted(tokens)),
                located,
                canon.text(),
                Stopwords.contentRatio(tokens));
    }

    /**
     * Weight the SimHash features so boilerplate does not dominate. Function words are not dropped:
     * a segment made entirely of them would otherwise sign as zero and collide with every other such
     * segment, which is worse than signing weakly.
     */
    private static List<SimHash.Feature> weighted(List<String> tokens) {
        return tokens.stream()
                .map(token -> new SimHash.Feature(
                        Hashing.hashToken(token), Stopwords.isStopword(token) ? STOPWORD_WEIGHT : 1.0))
                .toList();
    }
}

package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.UnknownFormatError;
import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * An ordered collection of readers that can pick one for a document.
 *
 * <p>Detection is a plain argmax over sniff scores with deterministic tie-breaking: registration
 * order decides, so adding a reader never silently changes how an existing corpus is parsed unless
 * it actually scores higher.
 */
public final class ReaderRegistry {

    /** The lowest sniff score that counts as recognising a document. */
    public static final double MIN_CONFIDENCE = 0.01;

    private static final Logger LOG = Log.get("ingest.registry");

    private final List<Reader> readers = new ArrayList<>();

    /**
     * Which reader won, and by how much.
     *
     * @param reader the winning reader
     * @param score its sniff score
     * @param runnerUp the name of the next best reader, if there was one
     */
    public record Detection(Reader reader, double score, Optional<String> runnerUp) {

        /** The winning reader's name. */
        public String name() {
            return reader.name();
        }
    }

    /** Add {@code reader}, whose name must be new. */
    public void register(Reader reader) {
        if (readers.stream().anyMatch(existing -> existing.name().equals(reader.name()))) {
            throw new IngestError("a reader with this name is already registered", Context.of("name", reader.name()));
        }
        readers.add(reader);
    }

    /** The registered reader names, in registration order. */
    public List<String> names() {
        return readers.stream().map(Reader::name).toList();
    }

    /** The reader named {@code name}. */
    public Reader get(String name) {
        return readers.stream()
                .filter(reader -> reader.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new UnknownFormatError(
                        "no reader with this name", Context.of("name", name, "known", String.join(",", names()))));
    }

    private record Scored(double score, int position, Reader reader) {}

    /** The highest scoring reader for {@code document}. */
    public Detection detect(SourceDocument document) {
        if (readers.isEmpty()) {
            throw new UnknownFormatError("no readers registered", Context.of("origin", document.origin()));
        }
        List<Scored> scored = new ArrayList<>();
        for (int position = 0; position < readers.size(); position++) {
            Reader reader = readers.get(position);
            scored.add(new Scored(reader.sniff(document), position, reader));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparingInt(Scored::position));
        Scored best = scored.getFirst();
        if (best.score() < MIN_CONFIDENCE) {
            throw new UnknownFormatError(
                    "no reader recognised the document",
                    Context.of(
                            "origin",
                            document.origin(),
                            "best",
                            best.reader().name(),
                            "score",
                            Floats.round(best.score(), 4)));
        }
        Optional<String> runnerUp =
                scored.size() > 1 ? Optional.of(scored.get(1).reader().name()) : Optional.empty();
        return new Detection(best.reader(), best.score(), runnerUp);
    }

    /** Parse {@code document} with the detected reader. */
    public Instrument read(SourceDocument document) {
        Detection detection = detect(document);
        LOG.atDebug()
                .addKeyValue("reader", detection.name())
                .addKeyValue("score", detection.score())
                .addKeyValue("origin", document.origin())
                .log("reader detected");
        return detection.reader().read(document);
    }

    /** Parse {@code document} with the reader named {@code formatName}. */
    public Instrument read(SourceDocument document, String formatName) {
        Reader reader = get(formatName);
        LOG.atDebug().addKeyValue("reader", reader.name()).log("reading with a pinned reader");
        return reader.read(document);
    }

    /**
     * The registry the CLI and the service use.
     *
     * <p>Exactly one amendment reader is registered. Registering both would make the detection
     * order decide which reading a corpus was indexed under, which is a decision that belongs in the
     * configuration.
     */
    public static ReaderRegistry defaultRegistry(Reading reading) {
        ReaderRegistry registry = new ReaderRegistry();
        registry.register(new AmendmentReader(reading));
        registry.register(new EnrolledReader());
        registry.register(new PlaintextReader());
        return registry;
    }

    /** The default registry for the reading named {@code reading}. */
    public static ReaderRegistry defaultRegistry(String reading) {
        return defaultRegistry(Reading.fromValue(reading));
    }

    /** The default registry under the proposed reading. */
    public static ReaderRegistry defaultRegistry() {
        return defaultRegistry(Reading.PROPOSED);
    }
}

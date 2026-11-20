package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A set of documents to ingest together.
 *
 * <p>Two ways in. A YAML manifest names every document and its hints explicitly, and that is what a
 * maintained corpus uses. Discovery reads the hints out of file names shaped {@code
 * <jurisdiction>_<session>_<number>.txt}, which is what a scratch directory of scraped bills looks
 * like. Discovery never guesses a jurisdiction from a parent directory or from the text: a file that
 * does not follow the convention is reported as skipped rather than loaded under a wrong key,
 * because a wrongly attributed instrument corrupts every later finding about where language
 * originated.
 *
 * @param root the directory documents are read from
 * @param entries the documents, in order
 * @param skipped files discovery could not identify
 */
public record Corpus(Path root, List<Entry> entries, List<Path> skipped) implements Iterable<Corpus.Entry> {

    private static final Logger LOG = Log.get("corpus");
    private static final Set<String> TEXT_SUFFIXES = Set.of(".txt", ".text", ".bill");

    /** Copies the lists. */
    public Corpus {
        entries = List.copyOf(entries);
        skipped = List.copyOf(skipped);
    }

    /**
     * One document and what the corpus knows about it.
     *
     * @param path where it is
     * @param hints the identity and metadata it is filed under
     * @param formatName the reader to use, when the corpus pins one
     */
    public record Entry(Path path, Map<String, Object> hints, Optional<String> formatName) {

        /** Copies the hints, keeping their order. */
        public Entry {
            hints = Collections.unmodifiableMap(new LinkedHashMap<>(hints));
        }

        /** The document, read from disk. */
        public SourceDocument source() {
            return SourceDocument.fromPath(path, hints);
        }
    }

    /** Number of documents. */
    public int size() {
        return entries.size();
    }

    @Override
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }

    /** The jurisdictions the entries are filed under, sorted. */
    public List<String> jurisdictions() {
        Set<String> found = new TreeSet<>();
        for (Entry entry : entries) {
            String jurisdiction = String.valueOf(entry.hints().getOrDefault("jurisdiction", ""));
            if (!jurisdiction.isEmpty()) {
                found.add(jurisdiction);
            }
        }
        return List.copyOf(found);
    }

    private static Optional<Map<String, Object>> parseName(Path path) {
        String[] parts = stem(path).split("_", -1);
        if (parts.length < 3) {
            return Optional.empty();
        }
        String jurisdiction = parts[0];
        String session = parts[1];
        String number = String.join("_", List.of(parts).subList(2, parts.length));
        if (jurisdiction.isEmpty() || session.isEmpty() || number.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("jurisdiction", jurisdiction);
        hints.put("session", session);
        hints.put("number", number);
        return Optional.of(hints);
    }

    /** The file name without its last suffix; a leading dot does not start a suffix. */
    static String stem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(0, dot) : name;
    }

    /** The file name's last suffix, dot included, or empty. */
    static String suffix(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : "";
    }

    /** Build a corpus from the file names of the text documents under {@code root}. */
    public static Corpus discover(Path root) {
        return discover(root, TEXT_SUFFIXES);
    }

    /** Build a corpus from the file names under {@code root} carrying one of {@code suffixes}. */
    public static Corpus discover(Path root, Set<String> suffixes) {
        if (!Files.isDirectory(root)) {
            throw new IngestError("corpus root is not a directory", Context.of("root", root.toString()));
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).sorted(Corpus::byParts).toList();
        } catch (IOException exc) {
            throw new IngestError("corpus root cannot be read", Context.of("root", root.toString()), exc);
        }
        List<Entry> entries = new ArrayList<>();
        List<Path> skipped = new ArrayList<>();
        for (Path path : files) {
            if (!suffixes.contains(suffix(path).toLowerCase(Locale.ROOT))) {
                continue;
            }
            Optional<Map<String, Object>> hints = parseName(path);
            if (hints.isEmpty()) {
                skipped.add(path);
                continue;
            }
            entries.add(new Entry(path, hints.get(), Optional.empty()));
        }
        LOG.atDebug()
                .addKeyValue("root", root.toString())
                .addKeyValue("found", entries.size())
                .addKeyValue("skipped", skipped.size())
                .log("corpus discovered");
        return new Corpus(root, entries, skipped);
    }

    /** Paths compare component by component, so {@code a/b} sorts before {@code a-b}. */
    private static int byParts(Path left, Path right) {
        Iterator<Path> one = left.iterator();
        Iterator<Path> two = right.iterator();
        while (one.hasNext() && two.hasNext()) {
            int order = one.next().toString().compareTo(two.next().toString());
            if (order != 0) {
                return order;
            }
        }
        return Boolean.compare(one.hasNext(), two.hasNext());
    }

    /** Build a corpus from a YAML manifest. */
    public static Corpus loadManifest(Path manifest) {
        Object raw;
        try {
            raw = Settings.yaml().load(Files.readString(manifest, StandardCharsets.UTF_8));
        } catch (IOException exc) {
            throw new ConfigError("corpus manifest cannot be read", Context.of("path", manifest.toString()), exc);
        } catch (YAMLException exc) {
            throw new ConfigError("corpus manifest is not valid YAML", Context.of("path", manifest.toString()), exc);
        }
        if (!(raw instanceof Map<?, ?> document) || !document.containsKey("documents")) {
            throw new ConfigError("corpus manifest needs a documents list", Context.of("path", manifest.toString()));
        }
        if (!(document.get("documents") instanceof List<?> documents)) {
            throw new ConfigError("corpus manifest documents must be a list", Context.of("path", manifest.toString()));
        }
        Object rootName = document.containsKey("root") ? document.get("root") : ".";
        Path base = manifest.getParent() == null ? Path.of("") : manifest.getParent();
        Path root = join(base, String.valueOf(rootName));
        List<Entry> entries = new ArrayList<>();
        for (int position = 0; position < documents.size(); position++) {
            if (!(documents.get(position) instanceof Map<?, ?> item) || !item.containsKey("path")) {
                throw new ConfigError(
                        "corpus entry needs a path", Context.of("path", manifest.toString(), "entry", position));
            }
            Map<String, Object> hints = new LinkedHashMap<>();
            item.forEach((key, value) -> {
                if (!"path".equals(key) && !"format".equals(key)) {
                    hints.put(String.valueOf(key), value);
                }
            });
            Object format = item.get("format");
            entries.add(new Entry(
                    join(root, String.valueOf(item.get("path"))),
                    hints,
                    format == null ? Optional.empty() : Optional.of(String.valueOf(format))));
        }
        return new Corpus(root, entries, List.of());
    }

    /** {@code child} under {@code base}, dropping {@code .} components; an absolute child wins. */
    private static Path join(Path base, String child) {
        Path joined = child.startsWith("/") ? Path.of("/") : base;
        for (String part : child.split("/", -1)) {
            if (!part.isEmpty() && !part.equals(".")) {
                joined = joined.resolve(part);
            }
        }
        return joined;
    }

    /** Parse every entry in order with the default registry. */
    public static List<Instrument> readAll(Corpus corpus) {
        return readAll(corpus, ReaderRegistry.defaultRegistry());
    }

    /** Parse every entry in order, with the reader each entry pins or the detected one. */
    public static List<Instrument> readAll(Corpus corpus, ReaderRegistry registry) {
        List<Instrument> instruments = new ArrayList<>();
        for (Entry entry : corpus) {
            SourceDocument source = entry.source();
            instruments.add(
                    entry.formatName().map(name -> registry.read(source, name)).orElseGet(() -> registry.read(source)));
        }
        return instruments;
    }
}

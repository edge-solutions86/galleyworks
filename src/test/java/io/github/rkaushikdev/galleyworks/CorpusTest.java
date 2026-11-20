package io.github.rkaushikdev.galleyworks;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusTest {

    @TempDir
    Path tmp;

    private static Path writeBill(Path root, String name) throws IOException {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, makeBill("wy", "HB0142", MODEL_CLAUSE).rawText(), StandardCharsets.UTF_8);
        return path;
    }

    private Path manifest(String text) throws IOException {
        Path manifest = tmp.resolve("corpus.yaml");
        Files.writeString(manifest, text, StandardCharsets.UTF_8);
        return manifest;
    }

    @Test
    void discoveryReadsHintsFromTheFileName() throws IOException {
        writeBill(tmp, "wy_2025_HB0142.txt");
        Corpus corpus = Corpus.discover(tmp);
        assertThat(corpus.size()).isEqualTo(1);
        assertThat(corpus.entries().getFirst().hints())
                .isEqualTo(Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142"));
    }

    @Test
    void discoveryWalksSubdirectories() throws IOException {
        writeBill(tmp.resolve("wy"), "wy_2025_HB0142.txt");
        writeBill(tmp.resolve("mt"), "mt_2025_HB0311.txt");
        assertThat(Corpus.discover(tmp).size()).isEqualTo(2);
    }

    @Test
    void discoveryReportsItsJurisdictions() throws IOException {
        writeBill(tmp, "wy_2025_HB0142.txt");
        writeBill(tmp, "mt_2025_HB0311.txt");
        assertThat(Corpus.discover(tmp).jurisdictions()).containsExactly("mt", "wy");
    }

    @Test
    void aBadlyNamedFileIsSkippedNotGuessed() throws IOException {
        writeBill(tmp, "wy_2025_HB0142.txt");
        writeBill(tmp, "notes.txt");
        Corpus corpus = Corpus.discover(tmp);
        assertThat(corpus.size()).isEqualTo(1);
        assertThat(corpus.skipped().getFirst().getFileName()).hasToString("notes.txt");
    }

    @Test
    void unrelatedSuffixesAreIgnored() throws IOException {
        writeBill(tmp, "wy_2025_HB0142.txt");
        Files.write(tmp.resolve("wy_2025_HB0143.pdf"), "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));
        Corpus corpus = Corpus.discover(tmp);
        assertThat(corpus.size()).isEqualTo(1);
        assertThat(corpus.skipped()).isEmpty();
    }

    @Test
    void aNumberContainingUnderscoresSurvives() throws IOException {
        writeBill(tmp, "co_2025_SB_007.txt");
        assertThat(Corpus.discover(tmp).entries().getFirst().hints()).containsEntry("number", "SB_007");
    }

    @Test
    void discoveryNeedsADirectory() {
        assertThatThrownBy(() -> Corpus.discover(tmp.resolve("absent")))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    void entriesParseIntoInstruments() throws IOException {
        writeBill(tmp, "wy_2025_HB0142.txt");
        List<Instrument> instruments = Corpus.readAll(Corpus.discover(tmp));
        assertThat(instruments).hasSize(1);
        assertThat(instruments.getFirst().key().slug()).isEqualTo("wy/2025/lower/HB0142");
    }

    @Test
    void aManifestNamesItsDocuments() throws IOException {
        writeBill(tmp.resolve("bills"), "one.txt");
        Corpus corpus = Corpus.loadManifest(manifest("root: bills\n"
                + "documents:\n"
                + "  - path: one.txt\n"
                + "    jurisdiction: wy\n"
                + "    session: '2025'\n"
                + "    number: HB0142\n"));
        assertThat(corpus.size()).isEqualTo(1);
        assertThat(corpus.entries().getFirst().hints()).containsEntry("number", "HB0142");
        assertThat(Corpus.readAll(corpus).getFirst().key().jurisdiction()).isEqualTo("wy");
    }

    @Test
    void aManifestCanPinAReader() throws IOException {
        writeBill(tmp, "one.txt");
        Corpus corpus = Corpus.loadManifest(manifest("documents:\n"
                + "  - path: one.txt\n"
                + "    format: plaintext\n"
                + "    jurisdiction: wy\n"
                + "    session: '2025'\n"
                + "    number: HB0142\n"));
        assertThat(corpus.entries().getFirst().formatName()).contains("plaintext");
        assertThat(Corpus.readAll(corpus).getFirst().sourceFormat()).isEqualTo("plaintext");
    }

    @Test
    void aMissingManifestIsRejected() {
        assertThatThrownBy(() -> Corpus.loadManifest(tmp.resolve("absent.yaml")))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("cannot be read");
    }

    @Test
    void aManifestWithoutDocumentsIsRejected() throws IOException {
        Path path = manifest("root: bills\n");
        assertThatThrownBy(() -> Corpus.loadManifest(path))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("needs a documents list");
    }

    @Test
    void aManifestEntryWithoutAPathIsRejected() throws IOException {
        Path path = manifest("documents:\n  - jurisdiction: wy\n");
        assertThatThrownBy(() -> Corpus.loadManifest(path)).isInstanceOfSatisfying(ConfigError.class, caught -> {
            assertThat(caught.getMessage()).contains("needs a path");
            assertThat(caught.context()).containsEntry("entry", 0);
        });
    }

    @Test
    void aManifestWithAScalarDocumentsKeyIsRejected() throws IOException {
        Path path = manifest("documents: one.txt\n");
        assertThatThrownBy(() -> Corpus.loadManifest(path))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("must be a list");
    }

    @Test
    void brokenManifestYamlIsRejected() throws IOException {
        Path path = manifest("documents: [\n");
        assertThatThrownBy(() -> Corpus.loadManifest(path))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("not valid YAML");
    }
}

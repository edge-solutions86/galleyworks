package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.fingerprint.Codec;
import io.github.rkaushikdev.galleyworks.index.Schema;
import io.github.rkaushikdev.galleyworks.report.JsonWriter;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/** What a release promises: the version, the stable error codes and the on-disk format versions. */
class ReleaseTest {

    static final List<String> STABLE_CODES = List.of(
            "lexdrift.canon",
            "lexdrift.config",
            "lexdrift.error",
            "lexdrift.ingest",
            "lexdrift.ingest.format",
            "lexdrift.ingest.parse",
            "lexdrift.match",
            "lexdrift.store");

    private static final String BUILD = Cli.read(Path.of("build.gradle"));
    private static final String README = Cli.read(Path.of("README.md"));

    /** Every error class in the root package, one instance each. */
    private static List<LexdriftError> errors() throws IOException, ReflectiveOperationException {
        Path root = Path.of("src", "main", "java", "io", "github", "rkaushikdev", "galleyworks");
        List<LexdriftError> found = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> listing = Files.list(root)) {
            files = listing.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".java", "");
            Class<?> type = Class.forName(Lexdrift.class.getPackageName() + "." + name);
            if (LexdriftError.class.isAssignableFrom(type)) {
                found.add(instance(type.asSubclass(LexdriftError.class)));
            }
        }
        return found;
    }

    private static LexdriftError instance(Class<? extends LexdriftError> type)
            throws InstantiationException, IllegalAccessException, InvocationTargetException {
        for (Constructor<?> constructor : type.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 1 && parameters[0] == String.class) {
                return (LexdriftError) constructor.newInstance("x");
            }
            if (parameters.length == 2 && parameters[0] == String.class && parameters[1] == int.class) {
                return (LexdriftError) constructor.newInstance("x", 1);
            }
        }
        throw new IllegalStateException("no simple constructor on " + type.getName());
    }

    @Test
    void theVersionIsOneZero() {
        assertThat(Lexdrift.VERSION).isEqualTo("1.0.0");
        assertThat(BUILD).contains("version = '" + Lexdrift.VERSION + "'");
    }

    @Test
    void theErrorCodesAreThePublishedSet() throws Exception {
        Set<String> published = new TreeSet<>();
        errors().forEach(error -> published.add(error.code()));
        assertThat(published).containsExactlyElementsOf(STABLE_CODES);
    }

    @Test
    void everyCodeIsNamespaced() {
        assertThat(STABLE_CODES).allSatisfy(code -> assertThat(code).startsWith("lexdrift."));
    }

    @Test
    void theOnDiskVersionsArePinned() {
        assertThat(Codec.CODEC_VERSION).isEqualTo(1);
        assertThat(Schema.SCHEMA_VERSION).isEqualTo(4);
        assertThat(JsonWriter.FORMAT_VERSION).isEqualTo(1);
    }

    @Test
    void theProjectIsDescribed() {
        assertThat(BUILD)
                .contains("description = 'Passage-reuse and near-duplicate detection for legislative instruments'")
                .contains("'Implementation-Title'")
                .contains("'Implementation-Version'")
                .contains("'Implementation-Vendor': 'rkaushik-dev'");
    }

    @Test
    void theProjectSaysWhatItIsAbout() {
        String lowered = README.toLowerCase(Locale.ROOT);
        List<String> keywords = List.of("legislat", "near-duplicate", "minhash", "winnow", "reuse");
        assertThat(lowered).contains("legislat");
        assertThat(keywords.stream().filter(lowered::contains).count()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void theProjectNamesItsSource() {
        assertThat(BUILD).contains("'Implementation-URL': 'https://github.com/rkaushik-dev/galleyworks'");
        assertThat(README)
                .contains("https://github.com/rkaushik-dev/galleyworks")
                .contains("https://github.com/rkaushik-dev/galleyworks/issues");
    }

    @Test
    void theLicenceAndReadmeAreDeclared() {
        assertThat(Path.of("README.md")).isRegularFile();
        assertThat(README).contains("Licence: proprietary");
    }

    @ParameterizedTest
    @FieldSource("STABLE_CODES")
    void eachCodeHasAStatus(String code) throws Exception {
        LexdriftError error = errors().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseThrow();
        assertThat(error.httpStatus()).isBetween(400, 599);
    }
}

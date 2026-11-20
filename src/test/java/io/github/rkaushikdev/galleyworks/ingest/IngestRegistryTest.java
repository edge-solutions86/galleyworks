package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.UnknownFormatError;
import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry.Detection;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IngestRegistryTest {

    private static final Map<String, Object> HINTS =
            Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142");

    private static SourceDocument doc() {
        return new SourceDocument("AN ACT relating to water.\n", "t.txt", HINTS);
    }

    private static final class StubReader implements Reader {

        private final String name;
        private final double score;
        private int reads;

        StubReader(String name, double score) {
            this.name = name;
            this.score = score;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public double sniff(SourceDocument document) {
            return score;
        }

        @Override
        public Instrument read(SourceDocument document) {
            reads++;
            return new PlaintextReader().read(document);
        }
    }

    @Test
    void defaultRegistryHasTheFallback() {
        assertThat(ReaderRegistry.defaultRegistry().names())
                .containsExactly("amendment-proposed", "enrolled", "plaintext");
    }

    @Test
    void duplicateNamesAreRejected() {
        ReaderRegistry registry = new ReaderRegistry();
        registry.register(new StubReader("enrolled", 0.5));
        assertThatThrownBy(() -> registry.register(new StubReader("enrolled", 0.9)))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void getFindsAReaderByName() {
        assertThat(ReaderRegistry.defaultRegistry().get("plaintext").name()).isEqualTo("plaintext");
    }

    @Test
    void getReportsTheKnownNames() {
        assertThatThrownBy(() -> ReaderRegistry.defaultRegistry().get("nonesuch"))
                .isInstanceOfSatisfying(UnknownFormatError.class, caught -> {
                    assertThat(caught.getMessage()).contains("no reader with this name");
                    assertThat(caught.context()).containsEntry("known", "amendment-proposed,enrolled,plaintext");
                });
    }

    @Test
    void detectPicksTheHighestScore() {
        ReaderRegistry registry = new ReaderRegistry();
        registry.register(new StubReader("low", 0.2));
        registry.register(new StubReader("high", 0.8));
        Detection detection = registry.detect(doc());
        assertThat(detection.name()).isEqualTo("high");
        assertThat(detection.score()).isCloseTo(0.8, within(1e-9));
        assertThat(detection.runnerUp()).contains("low");
    }

    @Test
    void tiesGoToTheEarlierRegistration() {
        ReaderRegistry registry = new ReaderRegistry();
        registry.register(new StubReader("first", 0.5));
        registry.register(new StubReader("second", 0.5));
        assertThat(registry.detect(doc()).name()).isEqualTo("first");
    }

    @Test
    void detectOnAnEmptyRegistryIsAnError() {
        assertThatThrownBy(() -> new ReaderRegistry().detect(doc()))
                .isInstanceOf(UnknownFormatError.class)
                .hasMessageContaining("no readers registered");
    }

    @Test
    void detectRefusesScoresBelowTheFloor() {
        ReaderRegistry registry = new ReaderRegistry();
        registry.register(new StubReader("weak", 0.0));
        assertThatThrownBy(() -> registry.detect(doc())).isInstanceOfSatisfying(UnknownFormatError.class, caught -> {
            assertThat(caught.getMessage()).contains("no reader recognised");
            assertThat(caught.context()).containsEntry("best", "weak");
        });
    }

    @Test
    void singleReaderHasNoRunnerUp() {
        ReaderRegistry registry = new ReaderRegistry();
        registry.register(new PlaintextReader());
        assertThat(registry.detect(doc()).runnerUp()).isEmpty();
    }

    @Test
    void readUsesTheDetectedReader() {
        ReaderRegistry registry = new ReaderRegistry();
        StubReader winner = new StubReader("high", 0.9);
        registry.register(new StubReader("low", 0.1));
        registry.register(winner);
        registry.read(doc());
        assertThat(winner.reads).isEqualTo(1);
    }

    @Test
    void readCanPinAReader() {
        ReaderRegistry registry = new ReaderRegistry();
        StubReader loser = new StubReader("low", 0.1);
        registry.register(loser);
        registry.register(new StubReader("high", 0.9));
        registry.read(doc(), "low");
        assertThat(loser.reads).isEqualTo(1);
    }

    @Test
    void pinningAnUnknownReaderIsAnError() {
        assertThatThrownBy(() -> ReaderRegistry.defaultRegistry().read(doc(), "nonesuch"))
                .isInstanceOf(UnknownFormatError.class);
    }

    @Test
    void readReturnsAParsedInstrument() {
        Instrument instrument = ReaderRegistry.defaultRegistry().read(doc());
        assertThat(instrument.key().slug()).isEqualTo("wy/2025/lower/HB0142");
        assertThat(instrument.segments()).hasSize(1);
    }
}

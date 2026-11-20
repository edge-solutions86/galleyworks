package io.github.rkaushikdev.galleyworks.fingerprint;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.ShingleConfig;
import io.github.rkaushikdev.galleyworks.ShingleMode;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdaptiveShingleTest {

    private static final String SHORT_CLAUSE = "The board acts promptly.";
    private static final ShingleConfig CONFIG = new ShingleConfig().withWidth(5).withWinnowWindow(4);

    private static int wordCount(String text) {
        return Tokens.wordsOnly(Tokens.tokenize(text)).size();
    }

    @Test
    void aLongTextKeepsTheConfiguredWidth() {
        assertThat(Shingles.effectiveWidth(50, CONFIG)).isEqualTo(5);
    }

    @Test
    void aTextExactlyTheWidthKeepsIt() {
        assertThat(Shingles.effectiveWidth(5, CONFIG)).isEqualTo(5);
    }

    @Test
    void aShortTextNarrowsTheWindow() {
        assertThat(Shingles.effectiveWidth(3, CONFIG)).isEqualTo(3);
        assertThat(Shingles.effectiveWidth(4, CONFIG)).isEqualTo(4);
    }

    @Test
    void theWindowNeverFallsBelowTheFloor() {
        assertThat(Shingles.effectiveWidth(1, CONFIG)).isEqualTo(Shingles.MIN_ADAPTIVE_WIDTH);
        assertThat(Shingles.effectiveWidth(0, CONFIG)).isEqualTo(Shingles.MIN_ADAPTIVE_WIDTH);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 5, 40})
    void theWindowNeverExceedsTheConfiguredOne(int count) {
        assertThat(Shingles.effectiveWidth(count, CONFIG)).isLessThanOrEqualTo(CONFIG.width());
    }

    @Test
    void aShortSegmentNowProducesShingles() {
        assertThat(wordCount(SHORT_CLAUSE)).isLessThan(CONFIG.width());
        assertThat(Shingles.shinglesFor(SHORT_CLAUSE, CONFIG)).isNotEmpty();
        assertThat(Shingles.shinglesFor(SHORT_CLAUSE, CONFIG, 0, false)).isEmpty();
    }

    @Test
    void aLongSegmentIsUnaffected() {
        String text = IntStream.range(0, 30).mapToObj(n -> "word" + n).collect(Collectors.joining(" "));
        assertThat(Shingles.shinglesFor(text, CONFIG)).isEqualTo(Shingles.shinglesFor(text, CONFIG, 0, false));
    }

    @Test
    void characterModeNarrowsToo() {
        ShingleConfig config =
                new ShingleConfig().withMode(ShingleMode.CHARACTER).withWidth(9);
        assertThat(Shingles.shinglesFor("abc", config)).isNotEmpty();
        assertThat(Shingles.shinglesFor("abc", config, 0, false)).isEmpty();
    }

    @Test
    void aShortSubsectionIsFingerprinted() {
        SegmentFingerprint record = new Fingerprinter()
                .fingerprint(makeBill("wy", "HB0142", SHORT_CLAUSE)).stream()
                        .filter(found -> found.path().equals("1(a)"))
                        .findFirst()
                        .orElseThrow();
        assertThat(record.selected()).isNotEmpty();
    }

    @Test
    void aShortCopiedSubsectionIsMatched() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(makeBill("wy", "HB0142", SHORT_CLAUSE)));
        assertThat(new Matcher(index).matchInstrument(makeBill("mt", "HB0311", SHORT_CLAUSE)))
                .isNotEmpty();
    }

    @Test
    void anEmptyTextStillYieldsNothing() {
        assertThat(Shingles.shinglesFor("", CONFIG)).isEmpty();
    }
}

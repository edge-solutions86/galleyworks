package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MatchBoilerplateTest {

    private static final String RECITAL = "Notwithstanding any other provision of law, and in accordance with the\n"
            + "policy of this state, the following provisions apply to every district\n"
            + "organized under this chapter without regard to the date of organization.";

    private static final List<String> SUBJECTS = List.of(
            "well permits",
            "grazing leases",
            "ditch rights of way",
            "reservoir storage",
            "stock watering ponds",
            "drainage districts",
            "county fair premiums",
            "veterinary licences",
            "brand inspection fees",
            "predator control levies",
            "seed certification");

    /**
     * Twelve bills that share a recital and nothing else. Exactly one of them carries the model
     * clause, which is what makes the clause evidence of copying rather than more boilerplate.
     */
    private static MemoryIndex corpus() {
        MemoryIndex index = new MemoryIndex();
        for (int position = 0; position < 12; position++) {
            String tail;
            if (position == 1) {
                tail = MODEL_CLAUSE;
            } else if (position == 2) {
                tail = LOCAL_CLAUSE;
            } else {
                String subject = SUBJECTS.get(position % SUBJECTS.size());
                tail = "The district shall keep a register of " + subject + " and shall report\n"
                        + "annually to the county commissioners on the disposition of every\n"
                        + "application concerning " + subject + " received during the year.";
            }
            String body = RECITAL + "\n\n(b)  " + tail;
            index.addAll(fingerprints(makeBill("wy", String.format("HB%04d", position), body)));
        }
        return index;
    }

    private static Matcher learned(MemoryIndex index) {
        return Matcher.over(index, index.config(), 0.2, 3);
    }

    @Test
    void aDefaultMatcherHasNoFilter() {
        assertThat(new Matcher(new MemoryIndex()).boilerplate().size()).isZero();
    }

    @Test
    void overLearnsAFilterFromTheIndex() {
        MemoryIndex index = corpus();
        Matcher matcher = learned(index);
        assertThat(matcher.boilerplate().size()).isPositive();
        assertThat(matcher.boilerplate().segmentsSeen()).isEqualTo(index.size());
    }

    @Test
    void learningFromAnEmptyIndexSuppressesNothing() {
        assertThat(Matcher.over(new MemoryIndex()).boilerplate().size()).isZero();
    }

    @Test
    void theSharedRecitalIsSuppressed() {
        BoilerplateFilter filter = BoilerplateFilter.learn(corpus(), 0.2, 3);
        Instrument probe = makeBill("mt", "HB0311", RECITAL + "\n\n(b)  " + LOCAL_CLAUSE);
        SegmentFingerprint recital = fingerprints(probe).stream()
                .filter(record -> record.path().equals("1(a)"))
                .findFirst()
                .orElseThrow();
        assertThat(filter.keeps(recital).size())
                .isLessThan(recital.fingerprintValues().size());
    }

    @Test
    void filteringReducesTheMatchesOnBoilerplateAlone() {
        MemoryIndex index = corpus();
        Instrument probe =
                makeBill("mt", "HB0311", RECITAL + "\n\n(b)  The county shall keep a register of well permits.");
        List<SegmentMatch> plain = new Matcher(index).matchInstrument(probe);
        List<SegmentMatch> filtered = learned(index).matchInstrument(probe);
        assertThat(filtered.size()).isLessThan(plain.size());
    }

    @Test
    void aRealCopySurvivesTheFilter() {
        MemoryIndex index = corpus();
        Instrument probe = makeBill("mt", "HB0311", RECITAL + "\n\n(b)  " + MODEL_CLAUSE);
        assertThat(learned(index).matchInstrument(probe)).isNotEmpty();
    }

    @Test
    void aSegmentThatIsAllBoilerplateMatchesNothing() {
        MemoryIndex index = corpus();
        Set<Long> everything = new HashSet<>();
        index.forEach(record -> everything.addAll(record.fingerprintValues()));
        Matcher matcher = new Matcher(index, index.config(), BoilerplateFilter.of(everything));
        assertThat(matcher.matchInstrument(makeBill("mt", "HB0311", MODEL_CLAUSE)))
                .isEmpty();
    }

    @Test
    void anExplicitFilterIsKept() {
        BoilerplateFilter given = BoilerplateFilter.of(Set.of(1L, 2L, 3L));
        assertThat(new Matcher(new MemoryIndex(), new EngineConfig(), given).boilerplate())
                .isSameAs(given);
    }
}

package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.ConfigError;
import org.junit.jupiter.api.Test;

class CanonCacheTest {

    private static final String ACT = "(a)  The board shall publish a schedule under W.S. 41-3-101 not later than "
            + "sixty (60) days after the close of each irrigation season.";

    private static CachingPipeline cached() {
        return new CachingPipeline(Profiles.buildPipeline());
    }

    private static CachingPipeline cached(long limitBytes) {
        return new CachingPipeline(Profiles.buildPipeline(), limitBytes);
    }

    @Test
    void theFirstCallIsAMiss() {
        CachingPipeline pipeline = cached();
        pipeline.run(ACT);
        CachingPipeline.CacheStats stats = pipeline.stats();
        assertThat(stats.misses()).isEqualTo(1);
        assertThat(stats.hits()).isZero();
        assertThat(stats.hitRate()).isEqualTo(0.0);
    }

    @Test
    void aRepeatIsAHit() {
        CachingPipeline pipeline = cached();
        CanonResult first = pipeline.run(ACT);
        CanonResult second = pipeline.run(ACT);
        assertThat(second).isSameAs(first);
        assertThat(pipeline.stats().hits()).isEqualTo(1);
        assertThat(pipeline.stats().hitRate()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void theCachedResultIsCorrect() {
        CachingPipeline pipeline = cached();
        CanonResult plain = Profiles.buildPipeline().run(ACT);
        assertThat(pipeline.run(ACT).text()).isEqualTo(plain.text());
        assertThat(pipeline.run(ACT).text()).isEqualTo(plain.text());
    }

    @Test
    void aDifferentConfigIsADifferentEntry() {
        CachingPipeline pipeline = cached();
        CanonResult defaults = pipeline.run(ACT);
        CanonResult verbatim = pipeline.run(ACT, Profiles.profile("verbatim"));
        assertThat(defaults.text()).isNotEqualTo(verbatim.text());
        assertThat(pipeline.stats().misses()).isEqualTo(2);
        assertThat(pipeline.stats().hits()).isZero();
    }

    @Test
    void aDifferentTextIsADifferentEntry() {
        CachingPipeline pipeline = cached();
        pipeline.run(ACT);
        pipeline.run(ACT.replace("sixty", "ninety"));
        assertThat(pipeline.stats().entries()).isEqualTo(2);
    }

    @Test
    void theRuleNamesPassThrough() {
        assertThat(cached().ruleNames()).isEqualTo(Profiles.buildPipeline().ruleNames());
    }

    @Test
    void aTinyLimitEvicts() {
        CachingPipeline pipeline = cached(ACT.length() * 2L + 8);
        pipeline.run(ACT);
        pipeline.run(ACT.replace("sixty", "ninety"));
        assertThat(pipeline.stats().entries()).isEqualTo(1);
    }

    @Test
    void anEntryLargerThanTheLimitIsNotStored() {
        CachingPipeline pipeline = cached(8);
        pipeline.run(ACT);
        assertThat(pipeline.stats().entries()).isZero();
        assertThat(pipeline.stats().bytesHeld()).isZero();
    }

    @Test
    void evictionKeepsTheMostRecentlyUsed() {
        String other = ACT.replace("sixty", "ninety");
        CachingPipeline pipeline = cached(ACT.length() * 2L + 8);
        pipeline.run(ACT);
        pipeline.run(ACT);
        pipeline.run(other);
        assertThat(pipeline.stats().entries()).isEqualTo(1);
        int before = pipeline.stats().misses();
        pipeline.run(other);
        assertThat(pipeline.stats().misses()).isEqualTo(before);
    }

    @Test
    void clearEmptiesTheCache() {
        CachingPipeline pipeline = cached();
        pipeline.run(ACT);
        pipeline.clear();
        assertThat(pipeline.stats().entries()).isZero();
        assertThat(pipeline.stats().bytesHeld()).isZero();
    }

    @Test
    void aNegativeLimitIsRejected() {
        assertThatThrownBy(() -> new CachingPipeline(Profiles.buildPipeline(), -1))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void anExplicitConfigIsHonoured() {
        CachingPipeline pipeline = cached();
        CanonResult plain = pipeline.run(ACT, new CanonConfig().withFoldCase(false));
        assertThat(plain.text()).contains("The board");
    }
}

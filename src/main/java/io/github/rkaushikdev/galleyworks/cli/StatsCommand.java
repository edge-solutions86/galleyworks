package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.index.Index;
import io.github.rkaushikdev.galleyworks.index.Statistics;
import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * {@code lexdrift stats}: describe what an index holds.
 *
 * <p>Read only and cheap enough to run before every query session. It answers "am I pointed at the
 * corpus I think I am", which is the question behind most reports that turn out to be wrong.
 */
@Command(
        name = "stats",
        header = "describe what an index holds",
        customSynopsis = "lexdrift stats [OPTIONS]",
        description = "Describe the index named by --index.")
public final class StatsCommand extends Common.Base {

    /** A fingerprint shared by this share of the segments makes the index unselective. */
    public static final double WIDE_POSTING_SHARE = 0.5;

    @Mixin
    Common.RequiredIndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Option(names = "--json", description = "emit the numbers as json")
    boolean asJson;

    @Override
    public Integer call() {
        EngineConfig config = engineOptions.config();
        Index opened = Common.buildIndex(Optional.of(index.path()), config);
        Statistics.CorpusStats measured;
        try {
            measured = Statistics.describe(opened);
        } finally {
            Common.close(opened);
        }
        if (asJson) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("instruments", measured.instruments());
            payload.put("segments", measured.segments());
            payload.put("jurisdictions", measured.jurisdictions());
            payload.put("sessions", measured.sessions());
            payload.put("mean_tokens", Floats.round(measured.meanTokens(), 3));
            payload.put("mean_fingerprints", Floats.round(measured.meanFingerprints(), 3));
            payload.put("distinct_fingerprints", measured.distinctFingerprints());
            payload.put("total_fingerprints", measured.totalFingerprints());
            payload.put("selectivity", Floats.round(measured.selectivity(), 6));
            payload.put("widest_posting", measured.widestPosting());
            payload.put("engine_digest", measured.digest());
            line(Json.write(payload, Json.Style.indented()));
        } else {
            measured.lines().forEach(this::line);
        }
        if (measured.segments() > 0 && measured.widestPostingShare() >= WIDE_POSTING_SHARE) {
            line("warning: one fingerprint appears in half the corpus; "
                    + "consider a wider shingle or a stricter boilerplate filter");
            return Main.EXIT_FINDINGS;
        }
        return Main.EXIT_OK;
    }
}

package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.fingerprint.Hashing;
import io.github.rkaushikdev.galleyworks.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The whole configuration surface of the engine.
 *
 * <p>Every knob that changes what a fingerprint means lives here, because two indexes built with
 * different settings cannot be compared. {@link #digest()} gives a short stable string the index
 * store records, so a mismatch is caught on open rather than at query time when the answers are
 * merely wrong.
 *
 * @param ingest how documents are read
 * @param shingle how token streams are cut into shingles
 * @param lsh the signature width and banding
 * @param canon which normalization rules run
 * @param match the thresholds applied to candidates
 * @param report what a report shows by default
 */
public record EngineConfig(
        IngestConfig ingest,
        ShingleConfig shingle,
        LshConfig lsh,
        CanonConfig canon,
        MatchConfig match,
        ReportConfig report) {

    private static final Set<String> SECTIONS = Set.of("ingest", "shingle", "lsh", "canon", "match", "report");
    private static final Json.Style DIGEST_STYLE = Json.Style.compact().sorted().separators(",", ":");

    /** Requires every section. */
    public EngineConfig {
        Objects.requireNonNull(ingest, "ingest");
        Objects.requireNonNull(shingle, "shingle");
        Objects.requireNonNull(lsh, "lsh");
        Objects.requireNonNull(canon, "canon");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(report, "report");
    }

    /** Every section at its defaults. */
    public EngineConfig() {
        this(
                new IngestConfig(),
                new ShingleConfig(),
                new LshConfig(),
                new CanonConfig(),
                new MatchConfig(),
                new ReportConfig());
    }

    /**
     * Validate a nested mapping, as read from YAML or the environment.
     *
     * @throws ConfigError naming every problem found, when the mapping is not a valid configuration
     */
    public static EngineConfig fromMapping(Map<String, ?> data) {
        List<String> problems = ConfigFields.problems();
        for (String key : data.keySet()) {
            if (!SECTIONS.contains(key)) {
                problems.add(key + ": Extra inputs are not permitted");
            }
        }
        EngineConfig config = new EngineConfig(
                IngestConfig.read(data.get("ingest"), "ingest", problems),
                ShingleConfig.read(data.get("shingle"), "shingle", problems),
                LshConfig.read(data.get("lsh"), "lsh", problems),
                CanonConfig.read(data.get("canon"), "canon", problems),
                MatchConfig.read(data.get("match"), "match", problems),
                ReportConfig.read(data.get("report"), "report", problems));
        if (!problems.isEmpty()) {
            String detail = problems.size() + " validation error" + (problems.size() == 1 ? "" : "s")
                    + " for EngineConfig\n" + String.join("\n", problems);
            throw new ConfigError("engine configuration is invalid", Context.of("detail", detail));
        }
        return config;
    }

    /** The configuration as a nested mapping of plain values, section by section. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("ingest", ingest.toMapping());
        mapping.put("shingle", shingle.toMapping());
        mapping.put("lsh", lsh.toMapping());
        mapping.put("canon", canon.toMapping());
        mapping.put("match", match.toMapping());
        mapping.put("report", report.toMapping());
        return mapping;
    }

    /**
     * A short stable identifier for the settings that shape an index.
     *
     * <p>Only the ingest, shingle, LSH and canon sections count: match thresholds and report
     * defaults change what is shown, not what the index holds.
     */
    public String digest() {
        Map<String, Object> shaping = new LinkedHashMap<>();
        shaping.put("ingest", ingest.toMapping());
        shaping.put("shingle", shingle.toMapping());
        shaping.put("lsh", lsh.toMapping());
        shaping.put("canon", canon.toMapping());
        byte[] blob = Json.write(shaping, DIGEST_STYLE).getBytes(StandardCharsets.UTF_8);
        return Hashing.hex(Hashing.hashBytes(blob));
    }

    /** This configuration with the ingest section replaced. */
    public EngineConfig withIngest(IngestConfig value) {
        return new EngineConfig(value, shingle, lsh, canon, match, report);
    }

    /** This configuration with the shingle section replaced. */
    public EngineConfig withShingle(ShingleConfig value) {
        return new EngineConfig(ingest, value, lsh, canon, match, report);
    }

    /** This configuration with the LSH section replaced. */
    public EngineConfig withLsh(LshConfig value) {
        return new EngineConfig(ingest, shingle, value, canon, match, report);
    }

    /** This configuration with the canon section replaced. */
    public EngineConfig withCanon(CanonConfig value) {
        return new EngineConfig(ingest, shingle, lsh, value, match, report);
    }

    /** This configuration with the match section replaced. */
    public EngineConfig withMatch(MatchConfig value) {
        return new EngineConfig(ingest, shingle, lsh, canon, value, report);
    }

    /** This configuration with the report section replaced. */
    public EngineConfig withReport(ReportConfig value) {
        return new EngineConfig(ingest, shingle, lsh, canon, match, value);
    }
}

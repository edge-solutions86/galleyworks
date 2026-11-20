package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.ShingleConfig;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.fingerprint.Winnow;
import io.github.rkaushikdev.galleyworks.report.ReportWriters;
import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Json;
import io.github.rkaushikdev.galleyworks.util.Repr;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** {@code lexdrift config}: show and check the settings a run would use. */
@Command(
        name = "config",
        header = "show and check the settings a run would use",
        customSynopsis = "lexdrift config [OPTIONS]",
        description = "Resolve the configuration and print it.")
public final class ConfigCommand extends Common.Base {

    /** A containment floor at or below this reports common phrasing as reuse. */
    private static final double LOW_CONTAINMENT = 0.1;

    @Mixin
    Common.IndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Option(names = "--json", description = "emit the settings as json")
    boolean asJson;

    @Option(names = "--list-profiles", description = "list the canonicalization profiles")
    boolean listProfiles;

    /** The numbers that follow from the settings rather than being set. */
    public static Map<String, Object> derived(EngineConfig settings) {
        ShingleConfig shingle = settings.shingle();
        Map<String, Object> numbers = new LinkedHashMap<>();
        numbers.put("guarantee_length_tokens", Winnow.guaranteeLength(shingle.width(), shingle.winnowWindow()));
        numbers.put("expected_density", Floats.round(Winnow.expectedDensity(shingle.winnowWindow()), 4));
        numbers.put("lsh_rows", settings.lsh().rows());
        numbers.put("lsh_threshold", Floats.round(settings.lsh().approximateThreshold(), 4));
        numbers.put("engine_digest", settings.digest());
        return numbers;
    }

    @Override
    public Integer call() {
        if (listProfiles) {
            String selected = engineOptions.profile == null || engineOptions.profile.isEmpty()
                    ? "default"
                    : engineOptions.profile;
            for (String name : new TreeSet<>(Profiles.PROFILES.keySet())) {
                line((name.equals(selected) ? "*" : " ") + " " + name);
            }
            return Main.EXIT_OK;
        }
        EngineConfig settings = engineOptions.config();
        Map<String, Object> numbers = derived(settings);
        if (asJson) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("settings", settings.toMapping());
            payload.put("derived", numbers);
            line(Json.write(payload, Json.Style.indented()));
            return Main.EXIT_OK;
        }
        line("profile: " + settings.canon().profile());
        line("amendment reading: " + settings.ingest().amendmentReading());
        line("report: confidence >= " + settings.report().minConfidence() + ", crossing only "
                + Repr.of(settings.report().crossingOnly()) + ", top "
                + settings.report()
                        .top()
                        .filter(top -> top != 0)
                        .map(String::valueOf)
                        .orElse("all"));
        line("shingle: " + settings.shingle().mode().value() + " width "
                + settings.shingle().width() + ", winnow window "
                + settings.shingle().winnowWindow());
        line("lsh: " + settings.lsh().permutations() + " permutations in "
                + settings.lsh().bands() + " bands of " + numbers.get("lsh_rows") + " rows");
        line("match: containment >= " + Floats.repr(settings.match().minContainment()) + ", passages >= "
                + settings.match().minPassageChars() + " characters, content >= "
                + Floats.repr(settings.match().minContentRatio()));
        line("guarantees a shared run of " + numbers.get("guarantee_length_tokens") + " tokens, keeping about "
                + Floats.percent((Double) numbers.get("expected_density"), 0) + " of shingles");
        line("banding is tuned for similarity near " + Floats.repr((Double) numbers.get("lsh_threshold")));
        line("engine digest " + numbers.get("engine_digest"));
        line("report formats: " + String.join(", ", ReportWriters.formatNames()));
        if (settings.match().minContainment() <= LOW_CONTAINMENT) {
            line("warning: a containment floor this low will report common phrasing");
            return Main.EXIT_FINDINGS;
        }
        return Main.EXIT_OK;
    }
}

package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.ReportSummary;
import io.github.rkaushikdev.galleyworks.report.ReportedMatch;
import io.github.rkaushikdev.galleyworks.util.Floats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/** {@code lexdrift explain}: print the case for each finding, argument only, never the text. */
@Command(
        name = "explain",
        header = "print the case for each finding",
        customSynopsis = "lexdrift explain [OPTIONS] DOCUMENT",
        description = "Explain what DOCUMENT reuses, without quoting it.")
public final class ExplainCommand extends Common.Base {

    @Parameters(paramLabel = "DOCUMENT")
    String document;

    @Mixin
    Common.RequiredIndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Mixin
    Common.DisplayOptions display;

    @Mixin
    QueryCommand.Hints hints;

    /** One block per finding, argument only. */
    public static List<String> renderCase(InstrumentReport report) {
        ReportSummary summary = report.summary();
        List<String> lines = new ArrayList<>();
        lines.add(report.key().slug() + ": " + summary.matchedSegments() + " of " + summary.totalSegments()
                + " segments matched, " + Floats.percent(summary.reuseRatio(), 1) + " of the document reused");
        if (report.isEmpty()) {
            lines.add("  no reuse found");
            return lines;
        }
        int position = 0;
        for (ReportedMatch match : report.matches()) {
            position++;
            lines.add("");
            lines.add(position + ". " + match.headline());
            lines.add("   " + match.characters() + " characters over "
                    + match.passages().size() + " passages, containment " + Floats.fixed(match.containment(), 2));
            match.reasons().forEach(reason -> lines.add("   - " + reason));
        }
        return lines;
    }

    @Override
    public Integer call() {
        Engine engine = engineOptions.engine(Optional.of(index.path()));
        try {
            Instrument instrument = engine.registry().read(hints.read(document));
            engine.texts().add(instrument);
            InstrumentReport shown = display.narrow(engine.analyse(instrument).report());
            renderCase(shown).forEach(this::line);
            boolean strong = shown.matches().stream().anyMatch(match -> match.confidence() == Confidence.STRONG);
            return strong ? Main.EXIT_FINDINGS : Main.EXIT_OK;
        } finally {
            Common.close(engine.index());
        }
    }
}

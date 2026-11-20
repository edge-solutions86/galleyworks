package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.index.Maintenance;
import io.github.rkaushikdev.galleyworks.index.Prune;
import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * {@code lexdrift check}: verify and repair an index.
 *
 * <p>The derived band and posting tables must agree with the stored records, and the engine
 * configuration must match the one the index was built with; the second is checked by opening the
 * index at all. {@code --repair} rebuilds the derived tables and vacuums. It never touches the
 * stored records, so it cannot lose anything that was indexed.
 */
@Command(
        name = "check",
        header = "verify and optionally repair an index",
        customSynopsis = "lexdrift check [OPTIONS]",
        description = "Check the index named by --index.")
public final class CheckCommand extends Common.Base {

    @Mixin
    Common.RequiredIndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Option(names = "--repair", description = "rebuild the derived tables and vacuum")
    boolean repair;

    @Option(names = "--prune", description = "also drop rows that answer nothing")
    boolean doPrune;

    @Override
    public Integer call() {
        try (SqliteIndex store = new SqliteIndex(index.path(), engineOptions.config())) {
            Maintenance.IntegrityReport report = Maintenance.checkIntegrity(store);
            line(report.summary());
            Prune.PruneReport prunable = Prune.findPrunable(store);
            line(prunable.summary());
            if (doPrune && !prunable.isClean()) {
                line("pruned " + Prune.prune(store).total() + " rows");
                prunable = Prune.findPrunable(store);
            }
            if (report.isClean() && prunable.isClean() && !repair) {
                return Main.EXIT_OK;
            }
            if (!repair) {
                line("run again with --repair to rebuild the derived tables");
                return Main.EXIT_FINDINGS;
            }
            Maintenance.CompactionReport outcome = Maintenance.compact(store);
            line("rebuilt " + outcome.rebuiltSegments() + " segments, reclaimed " + outcome.bytesReclaimed()
                    + " bytes");
            Maintenance.IntegrityReport after = Maintenance.checkIntegrity(store);
            line(after.summary());
            return after.isClean() ? Main.EXIT_OK : Main.EXIT_FINDINGS;
        }
    }
}

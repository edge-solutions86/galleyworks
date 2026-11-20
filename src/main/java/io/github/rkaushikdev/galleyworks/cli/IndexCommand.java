package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.Corpus;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.index.Freshness;
import io.github.rkaushikdev.galleyworks.index.IndexStats;
import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift index}: build or extend an index from a corpus.
 *
 * <p>{@code --incremental} compares each document against the digest the index recorded and
 * writes only what moved. It needs a durable index: an in-memory one has nothing to compare
 * against, so the flag is refused rather than silently doing a full run.
 */
@Command(
        name = "index",
        header = "build or extend an index from a corpus",
        customSynopsis = "lexdrift index [OPTIONS] SOURCE",
        description = "Index every document under SOURCE.")
public final class IndexCommand extends Common.Base {

    @Parameters(paramLabel = "SOURCE")
    String source;

    @Mixin
    Common.IndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Option(names = "--replace", description = "drop each instrument before adding it again")
    boolean replace;

    @Option(names = "--incremental", description = "index only what changed since the last run")
    boolean incremental;

    @Option(names = "--stats", description = "print index statistics when the run finishes")
    boolean stats;

    @Override
    public Integer call() {
        Engine engine = engineOptions.engine(index.path());
        try {
            return run(engine);
        } finally {
            Common.close(engine.index());
        }
    }

    private int run(Engine engine) {
        if (incremental && !(engine.index() instanceof SqliteIndex)) {
            throw new ConfigError("incremental indexing needs a durable index", Context.of("remedy", "pass --index"));
        }
        List<Instrument> instruments = Corpus.readAll(IngestCommand.loadCorpus(source), engine.registry());
        Set<String> wanted = new HashSet<>();
        instruments.forEach(instrument -> wanted.add(instrument.key().slug()));
        if (incremental && engine.index() instanceof SqliteIndex store) {
            Freshness.FreshnessReport plan = Freshness.planReindex(store, instruments);
            line(plan.summary());
            wanted.clear();
            wanted.addAll(plan.toIndex());
            for (String slug : plan.removed()) {
                line("dropped " + slug);
            }
        }
        int added = 0;
        int documents = 0;
        for (Instrument instrument : instruments) {
            if (!wanted.contains(instrument.key().slug())) {
                continue;
            }
            int written = write(engine, instrument, replace || incremental);
            added += written;
            documents++;
            line("indexed " + instrument.key().slug() + "  " + written + " segments");
        }
        line(documents + " instruments, " + added + " segments indexed");
        if (stats) {
            IndexStats summary = engine.index().stats();
            line("index now holds " + summary.instruments() + " instruments, " + summary.segments() + " segments, "
                    + summary.buckets() + " buckets, " + summary.postings() + " distinct fingerprints");
            line("engine digest " + summary.digest());
        }
        return Main.EXIT_OK;
    }

    /** Index one instrument, recording its identity and digest. */
    private static int write(Engine engine, Instrument instrument, boolean replace) {
        if (replace) {
            engine.index().removeInstrument(instrument.key());
        }
        if (engine.index() instanceof SqliteIndex store) {
            store.register(instrument);
        }
        return engine.add(instrument);
    }
}

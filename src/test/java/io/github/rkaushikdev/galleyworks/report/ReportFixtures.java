package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.util.Timestamps;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The reports the writer tests render, and a small CSV reader to check what they write. */
final class ReportFixtures {

    static final OffsetDateTime FIXED = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, java.time.ZoneOffset.UTC);

    private ReportFixtures() {}

    /** The Montana copy of the model clause, reported against the Wyoming original. */
    static InstrumentReport report() {
        return report(true);
    }

    /** As {@link #report()}, with or without the Wyoming text available to quote. */
    static InstrumentReport report(boolean resolved) {
        Instrument sourceBill = makeBill("wy", "HB0142", MODEL_CLAUSE);
        Instrument probe = makeBill("mt", "HB0311", MODEL_CLAUSE);
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(sourceBill));
        List<SegmentMatch> matches = new Matcher(index).matchInstrument(probe);
        TextSource source = resolved ? new MappingTextSource(List.of(sourceBill)) : new MappingTextSource();
        return Reports.buildReport(probe, matches, source, FIXED);
    }

    /** A report on an unrelated bill with nothing found. */
    static InstrumentReport empty() {
        return Reports.buildReport(makeBill("co", "SB0007", LOCAL_CLAUSE), List.of(), new MappingTextSource(), FIXED);
    }

    /** The midnight-free timestamp the fixtures are stamped with. */
    static String fixedSeconds() {
        return Timestamps.isoformatSeconds(FIXED);
    }

    /** Rows of {@code text} parsed as CSV with a header, by column. */
    static List<Map<String, String>> readCsv(String text) {
        List<List<String>> records = parse(text);
        List<Map<String, String>> rows = new ArrayList<>();
        if (records.isEmpty()) {
            return rows;
        }
        List<String> header = records.getFirst();
        for (List<String> record : records.subList(1, records.size())) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < record.size() ? record.get(i) : "");
            }
            rows.add(row);
        }
        return rows;
    }

    private static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}

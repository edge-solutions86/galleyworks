package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.FIXED;
import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.readCsv;
import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.report;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    private static String rendered(boolean header) {
        StringWriter stream = new StringWriter();
        CsvWriter.writeCsv(report(), stream, header);
        return stream.toString();
    }

    @Test
    void oneRowPerPassage() {
        InstrumentReport built = report();
        int expected = built.matches().stream()
                .mapToInt(match -> match.passages().size())
                .sum();
        assertThat(CsvWriter.flatten(built)).hasSize(expected);
    }

    @Test
    void everyColumnIsFilled() {
        Map<String, Object> row = CsvWriter.flatten(report()).getFirst();
        assertThat(new HashSet<>(row.keySet())).isEqualTo(new HashSet<>(CsvWriter.COLUMNS));
        assertThat(row.values())
                .allSatisfy(value -> assertThat(String.valueOf(value)).isNotEmpty());
    }

    @Test
    void theHeaderNamesTheColumns() {
        assertThat(List.of(rendered(true).split("\n")[0].split(",", -1))).isEqualTo(CsvWriter.COLUMNS);
    }

    @Test
    void theHeaderCanBeSuppressed() {
        assertThat(rendered(false)).doesNotStartWith("query_instrument");
    }

    @Test
    void theOutputParsesAsCsv() {
        List<Map<String, String>> rows = readCsv(rendered(true));
        assertThat(rows).isNotEmpty();
        assertThat(rows.getFirst())
                .containsEntry("query_instrument", "mt/2025/lower/HB0311")
                .containsEntry("other_jurisdiction", "wy");
    }

    @Test
    void writeCsvReturnsTheRowCount() {
        StringWriter stream = new StringWriter();
        int written = CsvWriter.writeCsv(report(), stream);
        assertThat(written).isEqualTo(readCsv(stream.toString()).size());
    }

    @Test
    void quotationsAreOneLine() {
        assertThat(CsvWriter.flatten(report())).allSatisfy(row -> assertThat(String.valueOf(row.get("query_text")))
                .doesNotContain("\n"));
    }

    @Test
    void aLongQuotationIsTruncated() {
        assertThat(CsvWriter.flatten(report())).allSatisfy(row -> assertThat(
                        String.valueOf(row.get("query_text")).length())
                .isLessThanOrEqualTo(400));
    }

    @Test
    void spansAreIntegers() {
        Map<String, Object> row = CsvWriter.flatten(report()).getFirst();
        assertThat(Integer.parseInt(String.valueOf(row.get("query_end"))))
                .isGreaterThan(Integer.parseInt(String.valueOf(row.get("query_start"))));
        assertThat(Integer.parseInt(String.valueOf(row.get("characters")))).isPositive();
    }

    @Test
    void scoresAreFixedWidth() {
        String containment =
                String.valueOf(CsvWriter.flatten(report()).getFirst().get("containment"));
        assertThat(containment.chars().filter(c -> c == '.').count()).isEqualTo(1);
        assertThat(containment.split("\\.")[1]).hasSize(6);
    }

    @Test
    void anEmptyReportWritesOnlyAHeader() {
        StringWriter stream = new StringWriter();
        int written = CsvWriter.writeCsv(
                Reports.buildReport(makeBill("co", "SB0007", LOCAL_CLAUSE), List.of(), new MappingTextSource(), FIXED),
                stream);
        assertThat(written).isZero();
        assertThat(stream.toString().strip()).isEqualTo(String.join(",", CsvWriter.COLUMNS));
    }

    @Test
    void rowsUseUnixLineEndings() {
        assertThat(rendered(true)).doesNotContain("\r\n");
    }
}

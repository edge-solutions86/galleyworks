package io.github.rkaushikdev.galleyworks.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoggingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @AfterEach
    void resetRoot() {
        Logger root = Log.packageLogger();
        root.detachAndStopAllAppenders();
        root.setLevel(null);
        root.setAdditive(true);
    }

    private List<Map<String, Object>> emitted() throws Exception {
        List<Map<String, Object>> records = new ArrayList<>();
        for (String line : err.toString(StandardCharsets.UTF_8).strip().split("\n")) {
            if (!line.isEmpty()) {
                records.add(JSON.readValue(line, new TypeReference<Map<String, Object>>() {}));
            }
        }
        return records;
    }

    @Test
    void getLoggerNamespacesUnderThePackage() {
        assertThat(Log.get("ingest").getName()).isEqualTo("lexdrift.ingest");
        assertThat(Log.get("lexdrift.match").getName()).isEqualTo("lexdrift.match");
    }

    @Test
    void jsonRecordsCarryExtraFields() throws Exception {
        Log.configure("info", true, err);
        Log.get("ingest")
                .atInfo()
                .addKeyValue("segments", 12)
                .addKeyValue("key", "wy/2025/lower/HB1")
                .log("parsed");
        List<Map<String, Object>> records = emitted();
        assertThat(records).hasSize(1);
        assertThat(records.getFirst())
                .containsEntry("message", "parsed")
                .containsEntry("segments", 12)
                .containsEntry("level", "info")
                .containsEntry("logger", "lexdrift.ingest");
    }

    @Test
    void boundContextIsAttachedAndPopped() throws Exception {
        Log.configure("info", true, err);
        org.slf4j.Logger log = Log.get("match");
        Log.bound(Map.of("run", "r-1"), () -> log.info("inside"));
        log.info("outside");
        List<Map<String, Object>> records = emitted();
        assertThat(records.get(0)).containsEntry("run", "r-1");
        assertThat(records.get(1)).doesNotContainKey("run");
    }

    @Test
    void boundContextsNest() throws Exception {
        Log.configure("info", true, err);
        org.slf4j.Logger log = Log.get("match");
        Log.bound(Map.of("run", "r-1"), () -> Log.bound(Map.of("stage", "align"), () -> log.info("deep")));
        Map<String, Object> record = emitted().getFirst();
        assertThat(record).containsEntry("run", "r-1").containsEntry("stage", "align");
    }

    @Test
    void explicitExtraBeatsBoundContext() throws Exception {
        Log.configure("info", true, err);
        Log.bound(
                Map.of("stage", "align"),
                () -> Log.get("match").atInfo().addKeyValue("stage", "score").log("shadow"));
        assertThat(emitted().getFirst()).containsEntry("stage", "score");
    }

    @Test
    void levelFiltersRecords() throws Exception {
        Log.configure("warning", true, err);
        org.slf4j.Logger log = Log.get("index");
        log.info("quiet");
        log.warn("loud");
        assertThat(emitted().stream().map(record -> record.get("message"))).containsExactly("loud");
    }

    @Test
    void configureReplacesHandlersRatherThanStacking() {
        Log.configure("info", true, err);
        Log.configure("info", true, err);
        List<Object> appenders = new ArrayList<>();
        Log.packageLogger().iteratorForAppenders().forEachRemaining(appenders::add);
        assertThat(appenders).hasSize(1);
    }

    @Test
    void unknownLevelIsRejected() {
        assertThatThrownBy(() -> Log.configure("chatty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown log level");
    }

    @Test
    void plainOutputIsNotJson() {
        Log.configure("info", false, err);
        Log.get("cli").info("hello");
        assertThat(err.toString(StandardCharsets.UTF_8).strip()).endsWith("lexdrift.cli hello");
    }

    @Test
    void formatterRecordsExceptions() throws Exception {
        JsonLayout layout = new JsonLayout();
        Logger logger = (Logger) Log.get("t");
        LoggingEvent event = new LoggingEvent(
                Logger.class.getName(), logger, Level.ERROR, "failed", new IllegalArgumentException("boom"), null);
        Map<String, Object> payload = JSON.readValue(layout.doLayout(event), new TypeReference<>() {});
        assertThat((String) payload.get("exception")).contains("IllegalArgumentException: boom");
    }
}

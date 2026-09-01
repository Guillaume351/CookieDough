package com.cookiebuild.cookiedough.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RuntimeVersionSnapshotPublisherTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void normalizesRuntimeVersionFormatsWithoutDroppingPrereleases() {
        assertEquals("26.2-112", RuntimeVersionSnapshotPublisher.normalizePaper("26.2-112-abcdef (MC: 26.2)"));
        assertEquals("1.21.4-120", RuntimeVersionSnapshotPublisher.normalizePaper("git-Paper-120 (MC: 1.21.4)"));
        assertEquals("2.11.2+1230", RuntimeVersionSnapshotPublisher.normalizeGeyser(
                "2.11.2-SNAPSHOT", "2.11.2-b1230 (git-master-1171591)"));
        assertNull(RuntimeVersionSnapshotPublisher.normalizeGeyser(
                "2.11.1-SNAPSHOT", "2.11.2-b1230 (git-master-1171591)"));
        assertNull(RuntimeVersionSnapshotPublisher.normalizeGeyser(
                "2.11.20-SNAPSHOT", "2.11.2-b1230 (git-master-1171591)"));
        assertEquals("2.2.5+140", RuntimeVersionSnapshotPublisher.normalizeFloodgate(
                "2.2.5-SNAPSHOT (b140-8780fa4)"));
        assertEquals("5.4.0-SNAPSHOT-748", RuntimeVersionSnapshotPublisher.normalizePlugin(
                "5.4.0-SNAPSHOT-748"));
    }

    @Test
    void writesOnlyTheVersionContractAtomically(@TempDir Path directory) throws Exception {
        Path output = directory.resolve("installed-versions.json");
        RuntimeVersionSnapshotPublisher.writeSnapshot(output, Map.of(
                "paper", "26.2-112",
                "geyser", "2.11.2+1230",
                "protocollib", "5.4.0-SNAPSHOT-748"), Instant.parse("2026-09-01T08:00:00Z"));

        JsonNode snapshot = MAPPER.readTree(Files.readString(output));
        assertEquals(1, snapshot.path("schemaVersion").asInt());
        assertEquals("2026-09-01T08:00:00Z", snapshot.path("generatedAt").asText());
        assertEquals("2.11.2+1230", snapshot.path("versions").path("geyser").asText());
        assertEquals("5.4.0-SNAPSHOT-748", snapshot.path("versions").path("protocollib").asText());
        assertEquals(3, snapshot.path("versions").size());
    }
}

package com.cookiebuild.cookiedough.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipUtilsTest {
    @TempDir
    Path temp;

    @Test
    void extractsFilesInsideDestination() throws Exception {
        Path zip = createZip("map/level.dat", "safe");
        Path destination = temp.resolve("game");

        ZipUtils.unzip(zip.toFile(), destination.toFile());

        assertEquals("safe", Files.readString(destination.resolve("map/level.dat")));
    }

    @Test
    void rejectsZipSlipEntries() throws Exception {
        Path zip = createZip("../outside.txt", "owned");
        Path destination = temp.resolve("game");

        assertThrows(IOException.class, () -> ZipUtils.unzip(zip.toFile(), destination.toFile()));
        assertFalse(Files.exists(temp.resolve("outside.txt")));
    }

    private Path createZip(String entryName, String contents) throws IOException {
        Path zip = Files.createTempFile(temp, "map", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return zip;
    }
}

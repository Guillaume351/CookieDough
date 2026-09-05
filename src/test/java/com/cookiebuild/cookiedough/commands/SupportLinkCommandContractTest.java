package com.cookiebuild.cookiedough.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SupportLinkCommandContractTest {
    @Test
    void pluginDescriptorExposesOnlyThePurposeScopedPlayerCommand() throws IOException {
        String pluginYaml;
        try (var stream = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            if (stream == null) throw new IOException("plugin.yml is missing");
            pluginYaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(pluginYaml.contains("  support:\n"));
        assertTrue(pluginYaml.contains("    usage: /<command> link\n"));
        assertTrue(pluginYaml.contains("    permission: cookiedough.support.link\n"));
        assertTrue(pluginYaml.contains("  cookiedough.support.link:\n"));
        assertTrue(pluginYaml.contains("    default: true\n"));
    }
}

package com.cookiebuild.cookiedough.admin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Publishes a secret-free, fresh snapshot of the versions loaded by Paper. */
public final class RuntimeVersionSnapshotPublisher {
    static final String OUTPUT_ENV = "COOKIEBUILD_RUNTIME_VERSIONS_FILE";
    private static final long REFRESH_TICKS = 20L * 300L;
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;
    private static final Pattern PAPER_VERSION = Pattern.compile("(?i)(?<![0-9])(\\d+(?:\\.\\d+)+)-(\\d+)(?![0-9])");
    private static final Pattern LEGACY_PAPER_VERSION = Pattern.compile("(?i)git-Paper-(\\d+).*\\(MC:\\s*(\\d+(?:\\.\\d+)+)\\)");
    private static final Pattern BUILD_VERSION = Pattern.compile("(?i)\\bv?(\\d+(?:\\.\\d+)+)(?:-SNAPSHOT)?-b(\\d+)\\b");
    private static final Pattern FLOODGATE_VERSION = Pattern.compile("(?i)\\bv?(\\d+(?:\\.\\d+)+)(?:-SNAPSHOT)?\\s*\\(b(\\d+)[^)]*\\)");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RuntimeVersionSnapshotPublisher() { }

    public static void start(JavaPlugin owner) {
        String configured = System.getenv(OUTPUT_ENV);
        if (configured == null || configured.isBlank()) return;

        final Path output;
        try {
            output = Path.of(configured).normalize();
            if (!output.isAbsolute() || output.getParent() == null || !"installed-versions.json".equals(output.getFileName().toString())) {
                throw new IllegalArgumentException(OUTPUT_ENV + " must be an absolute installed-versions.json path");
            }
        } catch (RuntimeException error) {
            owner.getLogger().severe("Runtime version snapshots are disabled: " + error.getMessage());
            return;
        }

        AtomicBoolean firstSuccess = new AtomicBoolean();
        AtomicLong lastFailureLog = new AtomicLong();
        Bukkit.getScheduler().runTaskTimer(owner, () -> {
            try {
                writeSnapshot(output, captureVersions(), Instant.now());
                if (firstSuccess.compareAndSet(false, true)) {
                    owner.getLogger().info("Runtime version snapshot publishing is active");
                }
            } catch (Exception error) {
                long now = System.currentTimeMillis();
                long previous = lastFailureLog.get();
                if (now - previous >= FAILURE_LOG_INTERVAL_MS && lastFailureLog.compareAndSet(previous, now)) {
                    owner.getLogger().warning("Could not publish runtime version snapshot: " + safeMessage(error));
                }
            }
        }, 20L, REFRESH_TICKS);
    }

    static Map<String, String> captureVersions() {
        Map<String, String> versions = new LinkedHashMap<>();
        put(versions, "paper", normalizePaper(Bukkit.getVersion()));

        Plugin geyser = plugin("Geyser-Spigot", "Geyser-Paper");
        if (geyser != null) {
            put(versions, "geyser", normalizeGeyser(
                    geyser.getPluginMeta().getVersion(), geyserBuildVersion(geyser)));
        }
        putPlugin(versions, "floodgate", true, "floodgate");
        putPlugin(versions, "viaversion", false, "ViaVersion");
        putPlugin(versions, "viabackwards", false, "ViaBackwards");
        putPlugin(versions, "protocollib", false, "ProtocolLib");
        putPlugin(versions, "oldcombatmechanics", false, "OldCombatMechanics");
        return versions;
    }

    private static void putPlugin(Map<String, String> versions, String id, boolean floodgate, String... names) {
        Plugin plugin = plugin(names);
        if (plugin == null) return;
        String declared = plugin.getPluginMeta().getVersion();
        put(versions, id, floodgate ? normalizeFloodgate(declared) : normalizePlugin(declared));
    }

    private static Plugin plugin(String... names) {
        for (String name : names) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
            if (plugin != null && plugin.isEnabled()) return plugin;
        }
        return null;
    }

    private static String geyserBuildVersion(Plugin geyser) {
        try (InputStream input = geyser.getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (input == null) return null;
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("git.build.version");
        } catch (Exception ignored) {
            return null;
        }
    }

    static String normalizePaper(String value) {
        Matcher current = PAPER_VERSION.matcher(String.valueOf(value));
        if (current.find()) return current.group(1) + "-" + current.group(2);
        Matcher legacy = LEGACY_PAPER_VERSION.matcher(String.valueOf(value));
        return legacy.find() ? legacy.group(2) + "-" + legacy.group(1) : null;
    }

    static String normalizeGeyser(String declared, String buildVersion) {
        Matcher build = BUILD_VERSION.matcher(String.valueOf(buildVersion));
        if (!build.find()) return null;
        String declaredRelease = normalizePlugin(declared);
        if (declaredRelease == null) return null;
        Matcher release = Pattern.compile("^(\\d+(?:\\.\\d+)+)(?:-|$)").matcher(declaredRelease);
        if (!release.find() || !release.group(1).equals(build.group(1))) return null;
        return build.group(1) + "+" + build.group(2);
    }

    static String normalizeFloodgate(String value) {
        Matcher build = FLOODGATE_VERSION.matcher(String.valueOf(value));
        return build.find() ? build.group(1) + "+" + build.group(2) : normalizePlugin(value);
    }

    static String normalizePlugin(String value) {
        String normalized = String.valueOf(value).trim();
        if (normalized.length() >= 2 && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'")))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.matches("v\\d.*")) normalized = normalized.substring(1);
        if (normalized.isBlank() || normalized.length() > 128 || normalized.matches(".*[\\r\\n\\t].*")) return null;
        return normalized;
    }

    private static void put(Map<String, String> versions, String id, String version) {
        if (version != null) versions.put(id, version);
    }

    static void writeSnapshot(Path output, Map<String, String> versions, Instant generatedAt) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("generatedAt", generatedAt.toString());
        ObjectNode values = root.putObject("versions");
        versions.forEach(values::put);

        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), ".installed-versions-", ".tmp");
        try {
            Files.writeString(temporary, MAPPER.writeValueAsString(root) + "\n", StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(temporary, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ,
                        PosixFilePermission.OTHERS_READ));
            } catch (UnsupportedOperationException ignored) { }
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) return root.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]", " ");
        return message.length() <= 200 ? message : message.substring(0, 200);
    }
}

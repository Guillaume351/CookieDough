package com.cookiebuild.cookiedough.admin;

import java.util.Map;

/** Environment-only configuration. The control plane is deliberately opt-in. */
public record AdminBridgeConfig(
        boolean enabled,
        String serverId,
        String rabbitUrl,
        String rabbitHost,
        int rabbitPort,
        String rabbitUsername,
        String rabbitPassword,
        String rabbitVirtualHost,
        String exchange,
        String commandQueue,
        long commandTtlMillis,
        long snapshotIntervalTicks,
        long reconnectDelayMillis) {

    public static AdminBridgeConfig fromEnvironment() {
        return from(System.getenv());
    }

    static AdminBridgeConfig from(Map<String, String> environment) {
        boolean enabled = Boolean.parseBoolean(value(environment, "ADMIN_BRIDGE_ENABLED", "false"));
        String serverId = value(environment, "ADMIN_BRIDGE_SERVER_ID", "minecraft-1");
        if (!serverId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("ADMIN_BRIDGE_SERVER_ID must contain only letters, numbers, dots, underscores, or dashes");
        }
        int rabbitPort = boundedInt(environment, "RABBITMQ_PORT", 5672, 1, 65535);
        long commandTtl = boundedLong(environment, "ADMIN_BRIDGE_COMMAND_TTL_MS", 300_000L, 10_000L, 3_600_000L);
        long snapshotSeconds = boundedLong(environment, "ADMIN_BRIDGE_SNAPSHOT_SECONDS", 5L, 2L, 60L);
        long reconnectSeconds = boundedLong(environment, "ADMIN_BRIDGE_RECONNECT_SECONDS", 5L, 1L, 60L);
        return new AdminBridgeConfig(
                enabled,
                serverId,
                blankToNull(environment.get("RABBITMQ_URL")),
                value(environment, "RABBITMQ_HOST", "rabbitmq"),
                rabbitPort,
                value(environment, "RABBITMQ_USERNAME", "guest"),
                value(environment, "RABBITMQ_PASSWORD", "guest"),
                value(environment, "RABBITMQ_VHOST", "/"),
                value(environment, "ADMIN_BRIDGE_EXCHANGE", "cookiebuild.admin"),
                value(environment, "ADMIN_BRIDGE_COMMAND_QUEUE", "cookiebuild.admin." + serverId + ".commands"),
                commandTtl,
                snapshotSeconds * 20L,
                reconnectSeconds * 1_000L);
    }

    public String serverCommandRoutingKey() {
        return "commands." + serverId;
    }

    public String eventRoutingKey(String kind) {
        return "events." + serverId + "." + kind;
    }

    public String deadLetterQueue() {
        return commandQueue + ".dead";
    }

    public String deadLetterRoutingKey() {
        return "dead.commands." + serverId;
    }

    private static String value(Map<String, String> environment, String name, String fallback) {
        String value = blankToNull(environment.get(name));
        return value == null ? fallback : value;
    }

    private static int boundedInt(Map<String, String> environment, String name, int fallback, int minimum, int maximum) {
        return (int) boundedLong(environment, name, fallback, minimum, maximum);
    }

    private static long boundedLong(Map<String, String> environment, String name, long fallback, long minimum, long maximum) {
        String raw = blankToNull(environment.get(name));
        long value = raw == null ? fallback : Long.parseLong(raw);
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

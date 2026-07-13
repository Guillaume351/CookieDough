package com.cookiebuild.cookiedough.game;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;

/** Pseudonymized structured activation and server-health telemetry. */
public final class FunnelTelemetry {
    public enum Event {
        JOINED, LOBBY_READY, PLAYER_DATA_READY, SELECTOR_OPENED, QUEUE_JOINED, QUEUE_LEFT,
        MATCH_STARTED, MATCH_COMPLETED, REMATCH_CLICKED, DISCONNECTED, KICKED,
        TUTORIAL_STARTED, TUTORIAL_COMPLETED, REWARD_CLAIMED, KIT_SELECTED, KIT_PURCHASED, NPC_SELECTED, FEEDBACK
    }

    private static volatile String salt;

    private FunnelTelemetry() {
    }

    public static void record(Player player, Event event, String details) {
        if (player == null) {
            return;
        }
        String context = "edition=" + edition(player)
                + " protocol=" + protocol(player)
                + " locale=" + player.locale().toLanguageTag()
                + " ready=" + PlayerWrapperListener.isPlayerDataReady(player.getUniqueId())
                + " mspt=" + averageTickTime();
        record(player.getUniqueId(), event, context + (details == null || details.isBlank() ? "" : " " + details));
    }

    public static void record(UUID playerId, Event event, String details) {
        Logger logger = CookieDough.getInstance().getLogger();
        String safeDetails = details == null ? "" : details.replace('\n', ' ').replace('\r', ' ');
        if (safeDetails.length() > 500) {
            safeDetails = safeDetails.substring(0, 500);
        }
        logger.info("[funnel] event=" + event.name().toLowerCase(Locale.ROOT)
                + " player=" + pseudonym(playerId) + (safeDetails.isBlank() ? "" : " " + safeDetails));
    }

    private static String pseudonym(UUID playerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((telemetrySalt() + ":" + playerId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String telemetrySalt() {
        if (salt != null) {
            return salt;
        }
        synchronized (FunnelTelemetry.class) {
            if (salt == null) {
                CookieDough plugin = CookieDough.getInstance();
                salt = plugin.getConfig().getString("telemetry.salt", "").trim();
                if (salt.isBlank()) {
                    salt = UUID.randomUUID().toString();
                    plugin.getConfig().set("telemetry.salt", salt);
                    plugin.saveConfig();
                }
            }
            return salt;
        }
    }

    private static String edition(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            boolean bedrock = (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, player.getUniqueId());
            return bedrock ? "bedrock" : "java";
        } catch (ReflectiveOperationException ignored) {
            return "java";
        }
    }

    private static String protocol(Player player) {
        try {
            return String.valueOf(player.getClass().getMethod("getProtocolVersion").invoke(player));
        } catch (ReflectiveOperationException ignored) {
            return "unknown";
        }
    }

    private static String averageTickTime() {
        try {
            Object value = Bukkit.getServer().getClass().getMethod("getAverageTickTime").invoke(Bukkit.getServer());
            return String.format(Locale.ROOT, "%.2f", ((Number) value).doubleValue());
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return "unknown";
        }
    }
}

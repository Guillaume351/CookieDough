package com.cookiebuild.cookiedough.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.bukkit.Bukkit;

import com.cookiebuild.cookiedough.CookieDough;

public class DiscordUtils {

    /**
     * Send a message to Discord using the specified webhook URL.
     * If the webhook URL is not defined, the message will not be sent.
     *
     * @param webhookUrl The Discord webhook URL to send the message to
     * @param message    The message to send
     */
    public static void sendDiscordMessage(String webhookUrl, String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            // Webhook URL is not defined, skip sending the message
            CookieDough.getInstance().getLogger()
                    .warning("Discord webhook URL is not defined. Skipping Discord message.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String escapedMessage = escapeJsonString(message);
                String jsonPayload = String.format("{\"content\":\"%s\"}", escapedMessage);
                byte[] out = jsonPayload.getBytes(StandardCharsets.UTF_8);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(out);
                }

                connection.getInputStream(); // Trigger the request
                connection.disconnect();
            } catch (Exception e) {
                CookieDough.getInstance().getLogger().severe("Failed to send Discord message: " + e.getMessage());
            }
        });
    }

    /**
     * Escape special JSON characters to prevent JSON injection.
     *
     * @param input The string to escape
     * @return The escaped string safe for JSON
     */
    private static String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }

        return input.replace("\\", "\\\\") // Escape backslashes first
                .replace("\"", "\\\"") // Escape quotes
                .replace("\b", "\\b") // Escape backspace
                .replace("\f", "\\f") // Escape form feed
                .replace("\n", "\\n") // Escape newline
                .replace("\r", "\\r") // Escape carriage return
                .replace("\t", "\\t"); // Escape tab
    }
}

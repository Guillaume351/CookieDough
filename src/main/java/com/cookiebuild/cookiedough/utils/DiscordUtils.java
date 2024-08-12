package com.cookiebuild.cookiedough.utils;

import com.cookiebuild.cookiedough.CookieDough;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
            CookieDough.getInstance().getLogger().warning("Discord webhook URL is not defined. Skipping Discord message.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(CookieDough.getInstance(), () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonPayload = String.format("{\"content\":\"%s\"}", message);
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
}

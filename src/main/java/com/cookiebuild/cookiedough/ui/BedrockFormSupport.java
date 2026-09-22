package com.cookiebuild.cookiedough.ui;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.Form;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/** Optional Floodgate boundary shared by gameplay modules; Java-only servers remain functional. */
public final class BedrockFormSupport {
    private BedrockFormSupport() { }

    public static boolean isBedrock(Player player) {
        if (player == null) return false;
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (RuntimeException | LinkageError | ClassNotFoundException ignored) {
            return false;
        }
    }

    public static boolean send(Player player, Form form) {
        if (!isBedrock(player)) return false;
        try {
            FloodgatePlayer target = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (target == null) return false;
            target.sendForm(form);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}

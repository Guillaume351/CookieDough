package com.cookiebuild.cookiedough.theme.music;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/** {@code /musique}: every player can switch the lobby music off and on again. */
public final class MusicCommand implements CommandExecutor {
    private final MusicService music;

    public MusicCommand(MusicService music) {
        this.music = music;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!music.active()) {
            player.sendMessage(LocaleManager.getMessage("theme.music.none", player.locale()));
            return true;
        }
        boolean on = music.toggle(player);
        player.sendMessage(LocaleManager.getMessage(on ? "theme.music.on" : "theme.music.off", player.locale()));
        return true;
    }
}

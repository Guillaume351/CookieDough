package com.cookiebuild.cookiedough.theme;

import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.ui.PlatformText;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Theme texts: tab list header and footer, MOTD second line (a Soirée Cookie
 * line still wins: its listener runs later), lobby scoreboard title and line,
 * and a periodic lobby announcement.
 */
final class ThemeTexts implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final JavaPlugin plugin;
    private ThemeDefinition theme;
    private BukkitTask announcer;

    ThemeTexts(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void activate(ThemeDefinition theme, Function<Player, Component> scoreboardLine) {
        deactivate();
        this.theme = theme;
        if (theme.texts().scoreboardTitle() != null) {
            LobbyScoreboard.setThemeTitle(LEGACY.deserialize(theme.texts().scoreboardTitle()));
        }
        LobbyScoreboard.setThemeLine(scoreboardLine);
        Bukkit.getOnlinePlayers().forEach(this::showTab);
        int minutes = theme.texts().announceMinutes();
        if (minutes > 0) {
            long period = 20L * 60L * minutes;
            announcer = Bukkit.getScheduler().runTaskTimer(plugin, this::announce, period / 2, period);
        }
    }

    void deactivate() {
        if (announcer != null) announcer.cancel();
        announcer = null;
        LobbyScoreboard.setThemeTitle(null);
        LobbyScoreboard.setThemeLine(null);
        if (theme != null) {
            for (Player player : Bukkit.getOnlinePlayers()) player.setPlayerListHeaderFooter("", "");
        }
        theme = null;
    }

    void showTab(Player player) {
        if (theme == null) return;
        String prefix = theme.messagePrefix();
        player.setPlayerListHeaderFooter(LocaleManager.getMessage(prefix + ".tab.header", player.locale()),
                LocaleManager.getMessage(prefix + ".tab.footer", player.locale()));
    }

    private void announce() {
        if (theme == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            if (cookiePlayer == null || cookiePlayer.getState() != PlayerState.LOBBY) continue;
            player.sendMessage(PlatformText.message(player, theme.messagePrefix() + ".announce"));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPing(ServerListPingEvent event) {
        ThemeDefinition current = theme;
        if (current == null || current.texts().motdLine() == null || current.texts().motdLine().isBlank()) return;
        String first = LEGACY.serialize(event.motd()).split("\n", 2)[0];
        event.motd(LEGACY.deserialize(first + "\n" + current.texts().motdLine()));
    }
}

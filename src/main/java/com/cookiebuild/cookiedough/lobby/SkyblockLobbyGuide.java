package com.cookiebuild.cookiedough.lobby;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Contextual, player-only breadcrumbs from a quiet lobby to the Skyblock bee. */
final class SkyblockLobbyGuide {
    private static final long TICK_PERIOD = 20L;
    private static final String LOBBY_WORLD = "lobby";
    private static final String SKYBLOCK = "Skyblock";

    private final CookieDough plugin;
    private final LobbyManager lobbyManager;
    private final SkyblockGuidanceCadence cadence;
    private BukkitTask task;

    SkyblockLobbyGuide(CookieDough plugin, LobbyManager lobbyManager) {
        this(plugin, lobbyManager, new SkyblockGuidanceCadence());
    }

    SkyblockLobbyGuide(CookieDough plugin, LobbyManager lobbyManager, SkyblockGuidanceCadence cadence) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
        this.cadence = cadence;
    }

    void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        cadence.clear();
    }

    private void tick() {
        Set<UUID> onlinePlayers = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getUniqueId()));
        cadence.retainPlayers(onlinePlayers);

        World lobbyWorld = Bukkit.getWorld(LOBBY_WORLD);
        GameNPC skyblockNpc = lobbyManager.getGameNpcByName(SKYBLOCK);
        if (lobbyWorld == null || skyblockNpc == null || skyblockNpc.getNPC() == null
                || !skyblockNpc.getNPC().isValid()) {
            long nowMillis = System.currentTimeMillis();
            for (UUID playerId : onlinePlayers) {
                cadence.evaluate(playerId, false, nowMillis);
            }
            return;
        }

        Location destination = skyblockNpc.getLocation();
        int lobbyPlayerCount = lobbyWorld.getPlayers().size();
        boolean skyblockAvailable = ModePopulationService.isPersistentActivityAvailable(SKYBLOCK);
        boolean readyMatch = ModePopulationService.hasReadyMatchForOneMorePlayer();
        long nowMillis = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
            double distanceSquared = sameWorld(player.getWorld(), destination.getWorld())
                    ? player.getLocation().distanceSquared(destination) : Double.NaN;
            SkyblockGuidancePolicy.Context context = new SkyblockGuidancePolicy.Context(
                    sameWorld(player.getWorld(), lobbyWorld),
                    cookiePlayer == null ? null : cookiePlayer.getState(),
                    lobbyPlayerCount,
                    cookiePlayer != null && GameManager.getGameOfPlayer(cookiePlayer) != null,
                    ActivityRegistry.owner(player.getUniqueId()) != null,
                    PlayerWrapperListener.isPlayerDataReady(player.getUniqueId()),
                    skyblockAvailable,
                    readyMatch,
                    plugin.getPartyManager().hasOnlinePartyCompanions(player.getUniqueId()),
                    plugin.getPracticeManager().isActive(player),
                    distanceSquared);
            SkyblockGuidanceCadence.Decision decision = cadence.evaluate(
                    player.getUniqueId(), SkyblockGuidancePolicy.shouldGuide(context), nowMillis);
            if (decision == SkyblockGuidanceCadence.Decision.NONE) continue;
            if (decision == SkyblockGuidanceCadence.Decision.START) {
                player.sendMessage(Component.text(LocaleManager.getMessage(
                        "lobby.skyblock_guide", player.locale()), NamedTextColor.GREEN));
            }
            renderTrail(player, destination);
        }
    }

    private static void renderTrail(Player player, Location npcLocation) {
        Location start = player.getLocation().clone().add(0.0, 0.3, 0.0);
        Location destination = npcLocation.clone().add(0.0, 1.0, 0.0);
        SkyblockGuidanceTrail.Point from = new SkyblockGuidanceTrail.Point(
                start.getX(), start.getY(), start.getZ());
        SkyblockGuidanceTrail.Point to = new SkyblockGuidanceTrail.Point(
                destination.getX(), destination.getY(), destination.getZ());
        for (SkyblockGuidanceTrail.Point point : SkyblockGuidanceTrail.between(from, to)) {
            player.spawnParticle(Particle.HAPPY_VILLAGER,
                    point.x(), point.y(), point.z(), 1, 0.03, 0.03, 0.03, 0.0);
        }
        player.spawnParticle(Particle.HAPPY_VILLAGER, destination, 3, 0.25, 0.35, 0.25, 0.0);
    }

    private static boolean sameWorld(World first, World second) {
        return first != null && first.equals(second);
    }
}

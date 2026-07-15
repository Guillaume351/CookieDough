package com.cookiebuild.cookiedough.listener;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerSession;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.retention.ChangelogCoordinator;
import com.cookiebuild.cookiedough.retention.ChangelogDigest;
import com.cookiebuild.cookiedough.retention.ChangelogEntry;
import com.cookiebuild.cookiedough.retention.PostgresChangelogRepository;
import com.cookiebuild.cookiedough.utils.DiscordUtils;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.cookiedough.service.MobilePromotionService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/** Owns the player lifecycle. No managed JPA entity crosses a worker boundary. */
public class PlayerWrapperListener implements Listener {
    private record SessionHandle(UUID sessionId, UUID playerId, Date startTime, long generation) {
    }

    private static PlayerWrapperListener instance;

    private final Map<UUID, LobbyScoreboard> playerLobbyScoreboards = new ConcurrentHashMap<>();
    private final Map<UUID, SessionHandle> activePlayerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> initializationFutures = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> queuedQuickPlay = ConcurrentHashMap.newKeySet();
    private final Set<UUID> newPlayerSessions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> disconnectReasons = new ConcurrentHashMap<>();
    private final ChangelogCoordinator changelog = new ChangelogCoordinator(new PostgresChangelogRepository());
    private final MobilePromotionService mobilePromotion = new MobilePromotionService();
    private final ExecutorService persistenceExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "CookieDough-player-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean acceptingPlayers = true;

    public PlayerWrapperListener() {
        instance = this;
    }

    public static Date getPlayerLoginTime(UUID playerId) {
        SessionHandle handle = instance == null ? null : instance.activePlayerSessions.get(playerId);
        return handle == null ? null : handle.startTime();
    }

    public static boolean isPlayerDataReady(UUID playerId) {
        return instance != null && instance.readyPlayers.contains(playerId);
    }

    public static void queueQuickPlayWhenReady(UUID playerId) {
        if (instance != null) {
            instance.queuedQuickPlay.add(playerId);
        }
    }

    public static void showLobbyScoreboard(Player player) {
        if (instance == null || player == null) {
            return;
        }
        LobbyScoreboard scoreboard = instance.playerLobbyScoreboards.computeIfAbsent(player.getUniqueId(), ignored ->
                new LobbyScoreboard(player));
        scoreboard.show();
    }

    public static void hideLobbyScoreboard(Player player) {
        if (instance == null || player == null) {
            return;
        }
        LobbyScoreboard scoreboard = instance.playerLobbyScoreboards.get(player.getUniqueId());
        if (scoreboard != null) {
            scoreboard.hide();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        FunnelTelemetry.record(player, FunnelTelemetry.Event.JOINED,
                "locale=" + player.locale().toLanguageTag());

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                    "player.joined.server", online.locale(), player.getName()));
        }

        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) {
            cookiePlayer = new CookiePlayer(player);
        }
        LobbyManager.teleportPlayerToLobby(cookiePlayer);

        player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("welcome.message", player.locale(), player.getName()));
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("Cookie Build", NamedTextColor.GOLD),
                Component.text(LocaleManager.getMessage("lobby.menu.subtitle", player.locale()),
                        NamedTextColor.YELLOW)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        player.sendMessage(Component.text(LocaleManager.getMessage("lobby.menu.action", player.locale()),
                        NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text(
                        LocaleManager.getMessage("lobby.menu.hover", player.locale()))))
                .clickEvent(ClickEvent.runCommand("/menu"))
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Discord", NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text("Open the Cookie Build Discord")))
                        .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com"))));

        if (!acceptingPlayers) {
            return;
        }

        Date joinTime = new Date();
        long generation = generations.computeIfAbsent(player.getUniqueId(), ignored -> new AtomicLong())
                .incrementAndGet();
        SessionHandle previous = activePlayerSessions.put(player.getUniqueId(),
                new SessionHandle(UUID.randomUUID(), player.getUniqueId(), joinTime, generation));
        readyPlayers.remove(player.getUniqueId());
        if (previous != null) {
            finalizeAfterInitialization(previous, joinTime);
        }
        SessionHandle handle = activePlayerSessions.get(player.getUniqueId());
        CompletableFuture<Void> initialized = CompletableFuture.runAsync(
                () -> initializeSession(handle, player.getName()), persistenceExecutor);
        initializationFutures.put(handle.sessionId(), initialized);
        initialized.whenComplete((ignored, error) -> {
            if (!acceptingPlayers) {
                return;
            }
            Bukkit.getScheduler().runTask(CookieDough.getInstance(), () -> {
            if (error != null) {
                CookieDough.getInstance().getLogger().severe("Failed to initialize player " + handle.playerId()
                        + ": " + rootMessage(error));
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Your profile could not be loaded. Please reconnect shortly.");
                }
                return;
            }
            SessionHandle current = activePlayerSessions.get(handle.playerId());
            if (current == null || current.generation() != handle.generation() || !player.isOnline()) {
                return;
            }
            readyPlayers.add(handle.playerId());
            LobbyScoreboard.invalidatePlayerCache(handle.playerId());
            showLobbyScoreboard(player);
            boolean newPlayer = newPlayerSessions.remove(handle.sessionId());
            FunnelTelemetry.record(player, FunnelTelemetry.Event.PLAYER_DATA_READY,
                    "session=" + handle.sessionId() + " new_player=" + newPlayer);
            showUnreadChangelog(player, handle);
            showMobileAppPromotion(player, handle);
            if (queuedQuickPlay.remove(handle.playerId())) {
                CookieDough.getInstance().getLobbyManager().requestQuickPlay(player);
            }
            });
        });

        sendPlayerStatusWebhook(player, true);
    }

    private void showUnreadChangelog(Player player, SessionHandle handle) {
        CompletableFuture.supplyAsync(() -> changelog.unread(handle.playerId()), persistenceExecutor)
                .whenComplete((digest, error) -> {
                    if (!acceptingPlayers) {
                        return;
                    }
                    Bukkit.getScheduler().runTask(CookieDough.getInstance(), () -> {
                        SessionHandle current = activePlayerSessions.get(handle.playerId());
                        if (current == null || current.generation() != handle.generation() || !player.isOnline()) {
                            return;
                        }
                        if (error != null) {
                            CookieDough.getInstance().getLogger().warning("Could not load changelog for "
                                    + player.getName() + ": " + rootMessage(error));
                            return;
                        }
                        if (digest == null || digest.isEmpty()) {
                            return;
                        }

                        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD
                                + LocaleManager.getMessage("changelog.join.header", player.locale()));
                        for (ChangelogEntry entry : digest.entries()) {
                            player.sendMessage(ChatColor.YELLOW + "• " + entry.title()
                                    + ChatColor.GRAY + " — " + entry.summary());
                        }
                        if (digest.hiddenCount() > 0) {
                            player.sendMessage(ChatColor.GRAY + LocaleManager.getMessage(
                                    "changelog.join.more", player.locale(), digest.hiddenCount()));
                        }
                        player.sendMessage(Component.text("www.cookie-build.com/changelog", NamedTextColor.AQUA)
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        LocaleManager.getMessage("changelog.join.link_hover", player.locale()))))
                                .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com/changelog")));

                        String playerName = player.getName();
                        CompletableFuture.runAsync(() -> changelog.acknowledge(handle.playerId(), digest),
                                persistenceExecutor).exceptionally(ackError -> {
                                    CookieDough.getInstance().getLogger().warning("Could not acknowledge changelog for "
                                            + playerName + ": " + rootMessage(ackError));
                                    return null;
                                });
                    });
                });
    }

    private void showMobileAppPromotion(Player player, SessionHandle handle) {
        CompletableFuture.supplyAsync(() -> mobilePromotion.claimPromotion(handle.playerId()), persistenceExecutor)
                .whenComplete((claimed, error) -> Bukkit.getScheduler().runTask(CookieDough.getInstance(), () -> {
                    SessionHandle current = activePlayerSessions.get(handle.playerId());
                    if (current == null || current.generation() != handle.generation() || !player.isOnline()) return;
                    if (error != null) {
                        CookieDough.getInstance().getLogger().warning("Could not evaluate the mobile app reminder: "
                                + rootMessage(error));
                        return;
                    }
                    if (!Boolean.TRUE.equals(claimed)) return;
                    player.sendMessage(Component.text(LocaleManager.getMessage(
                                    "app.promotion.message", player.locale()), NamedTextColor.LIGHT_PURPLE)
                            .append(Component.text("  "))
                            .append(Component.text(LocaleManager.getMessage(
                                            "app.promotion.download", player.locale()), NamedTextColor.AQUA)
                                    .hoverEvent(HoverEvent.showText(Component.text(LocaleManager.getMessage(
                                            "app.promotion.download_hover", player.locale()))))
                                    .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com/#mobile-app"))));
                    player.sendMessage(Component.text(LocaleManager.getMessage(
                                    "app.promotion.link", player.locale()), NamedTextColor.GOLD)
                            .hoverEvent(HoverEvent.showText(Component.text(LocaleManager.getMessage(
                                    "app.promotion.link_hover", player.locale()))))
                            .clickEvent(ClickEvent.runCommand("/app link")));
                }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        Player player = event.getPlayer();
        readyPlayers.remove(player.getUniqueId());
        queuedQuickPlay.remove(player.getUniqueId());
        cleanupScoreboard(player.getUniqueId());

        SessionHandle handle = activePlayerSessions.remove(player.getUniqueId());
        if (handle != null && acceptingPlayers) {
            finalizeAfterInitialization(handle, new Date());
        }
        String reason = disconnectReasons.remove(player.getUniqueId());
        if (reason == null) {
            reason = acceptingPlayers ? "quit" : "server_shutdown";
        }
        FunnelTelemetry.record(player, FunnelTelemetry.Event.DISCONNECTED, "reason=" + reason);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.sendMessage(ChatColor.GREEN + LocaleManager.getMessage(
                        "player.left.server", online.locale(), player.getName()));
            }
        }
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer != null) {
            cookiePlayer.disconnect();
        }
        LobbyScoreboard.invalidatePlayerCache(player.getUniqueId());
        CookieDough.getInstance().getChatManager().cleanup(player.getUniqueId());
        CookieDough.getInstance().getPracticeManager().stop(player, false);
        CookieDough.getInstance().getPartyManager().disconnect(player.getUniqueId());
        sendPlayerStatusWebhook(player, false);
    }

    private void initializeSession(SessionHandle handle, String playerName) {
        boolean newPlayer = false;
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                PlayerData playerData = em.find(PlayerData.class, handle.playerId());
                if (playerData == null) {
                    playerData = new PlayerData();
                    playerData.setId(handle.playerId());
                    playerData.setCreatedAt(handle.startTime());
                    em.persist(playerData);
                    newPlayer = true;
                }
                playerData.setName(playerName);
                playerData.setLastLogin(handle.startTime());

                PlayerSession session = new PlayerSession();
                session.setId(handle.sessionId());
                session.setPlayerData(playerData);
                session.setStartTime(handle.startTime());
                session.setDuration(0L);
                session.setServerCrash(true);
                em.persist(session);
                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
        if (newPlayer) {
            newPlayerSessions.add(handle.sessionId());
            DiscordUtils.sendDiscordMessage(System.getenv("DISCORD_NEW_PLAYER_WEBHOOK_URL"),
                    "A new player, " + playerName + ", has joined the server!");
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        String cause = "kick";
        try {
            Object exposedCause = event.getClass().getMethod("getCause").invoke(event);
            if (exposedCause != null) {
                cause = exposedCause.toString().toLowerCase(java.util.Locale.ROOT);
            }
        } catch (ReflectiveOperationException ignored) {
            // Older compatible Paper API without a structured cause.
        }
        disconnectReasons.put(event.getPlayer().getUniqueId(), cause);
        FunnelTelemetry.record(event.getPlayer(), FunnelTelemetry.Event.KICKED, "reason=" + cause);
    }

    private void finalizeAfterInitialization(SessionHandle handle, Date endTime) {
        CompletableFuture<Void> initialization = initializationFutures.getOrDefault(
                handle.sessionId(), CompletableFuture.completedFuture(null));
        initialization.handle((ignored, error) -> null).thenRunAsync(() -> {
            finalizeSession(handle, endTime);
            initializationFutures.remove(handle.sessionId());
        }, persistenceExecutor);
    }

    private void finalizeSession(SessionHandle handle, Date endTime) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                PlayerSession session = em.find(PlayerSession.class, handle.sessionId());
                if (session != null && (session.getEndTime() == null || session.isServerCrash())) {
                    session.setEndTime(endTime);
                    session.setDuration(Math.max(0L, endTime.getTime() - handle.startTime().getTime()));
                    session.setServerCrash(false);
                }
                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        } catch (RuntimeException error) {
            CookieDough.getInstance().getLogger().severe("Failed to finalize session " + handle.sessionId()
                    + ": " + rootMessage(error));
        }
    }

    /** Called before Hibernate closes. Waits a bounded time, then performs best-effort direct finalization. */
    public static void shutdownGracefully(Duration timeout) {
        if (instance == null) {
            return;
        }
        instance.acceptingPlayers = false;
        Date now = new Date();
        Map<UUID, SessionHandle> sessions = Map.copyOf(instance.activePlayerSessions);
        instance.activePlayerSessions.clear();
        long timeoutMillis = timeout.toMillis();
        try {
            CompletableFuture.allOf(instance.initializationFutures.values().toArray(CompletableFuture[]::new))
                    .get(Math.max(1L, timeoutMillis / 2), TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            CookieDough.getInstance().getLogger().warning("Some player profiles were still loading during shutdown");
        }
        for (SessionHandle handle : sessions.values()) {
            instance.finalizeAfterInitialization(handle, now);
        }
        instance.persistenceExecutor.shutdown();
        try {
            if (!instance.persistenceExecutor.awaitTermination(Math.max(1L, timeoutMillis / 2), TimeUnit.MILLISECONDS)) {
                CookieDough.getInstance().getLogger().warning("Timed out waiting for player session persistence");
                for (SessionHandle handle : sessions.values()) {
                    instance.finalizeSession(handle, now);
                }
                instance.persistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            instance.persistenceExecutor.shutdownNow();
        }
        instance.playerLobbyScoreboards.values().forEach(LobbyScoreboard::cleanup);
        instance.playerLobbyScoreboards.clear();
    }

    private void cleanupScoreboard(UUID playerId) {
        LobbyScoreboard scoreboard = playerLobbyScoreboards.remove(playerId);
        if (scoreboard != null) {
            scoreboard.cleanup();
        }
    }

    private void sendPlayerStatusWebhook(Player player, boolean joined) {
        String webhook = System.getenv("DISCORD_PLAYER_STATUS_WEBHOOK_URL");
        if (webhook == null || webhook.isBlank()) {
            return;
        }
        int count = Math.max(0, Bukkit.getOnlinePlayers().size() - (joined ? 0 : 1));
        DiscordUtils.sendDiscordMessage(webhook, player.getName() + (joined ? " has joined" : " has left")
                + " the server. Online players: " + count);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getPlayer().getWorld().getName().equals("lobby") && event.getPlayer().getLocation().getY() < 0) {
            LobbyManager.teleportPlayerToLobby(PlayerManager.getPlayer(event.getPlayer()));
        }
    }
}

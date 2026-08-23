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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.activity.PersistentActivity;
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
import jakarta.persistence.LockModeType;
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
    private final Map<UUID, String> queuedPersistentActivities = new ConcurrentHashMap<>();
    private final Set<UUID> newPlayerSessions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> onboardingPendingSessions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> onboardingCompletionRequested = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> disconnectReasons = new ConcurrentHashMap<>();
    private final PersistentActivityRecovery persistentRecovery = new PersistentActivityRecovery();
    private final ChangelogCoordinator changelog = new ChangelogCoordinator(new PostgresChangelogRepository());
    private final MobilePromotionService mobilePromotion = new MobilePromotionService();
    private final ExecutorService persistenceExecutor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "CookieDough-player-persistence");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean acceptingPlayers = true;
    private final BukkitTask persistentRecoveryTask;

    public PlayerWrapperListener() {
        instance = this;
        persistentRecoveryTask = Bukkit.getScheduler().runTaskTimer(CookieDough.getInstance(),
                this::retryPersistentRecoveries, 20L, 20L);
    }

    /**
     * Moves a player into a non-destructive holding state after an asynchronous
     * persistent activity admission fails. The durable marker, inventory and
     * location are deliberately preserved for a later retry.
     */
    public static void recoverPersistentActivity(Player player, String activityName) {
        PlayerWrapperListener current = instance;
        if (current == null || player == null || activityName == null || activityName.isBlank()) return;
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) cookiePlayer = new CookiePlayer(player);
        current.holdPersistentActivity(player, cookiePlayer, activityName, true);
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
            instance.queuedPersistentActivities.remove(playerId);
            instance.queuedQuickPlay.add(playerId);
        }
    }

    /** Queues the last persistent destination selected while player data loads. */
    public static void queueActivityWhenReady(UUID playerId, String activityName) {
        if (instance != null && playerId != null && activityName != null && !activityName.isBlank()) {
            instance.queuedQuickPlay.remove(playerId);
            instance.queuedPersistentActivities.put(playerId, activityName);
        }
    }

    /**
     * Marks onboarding complete only after a player explicitly chooses one of its
     * actions. Merely displaying or closing the menu is intentionally not enough,
     * so an interrupted onboarding is offered again after reconnecting.
     */
    public static void completeOnboarding(Player player, String action) {
        PlayerWrapperListener current = instance;
        if (current == null || player == null || !OnboardingCompletionPolicy.completes(action)) {
            return;
        }
        SessionHandle handle = current.activePlayerSessions.get(player.getUniqueId());
        if (handle == null || !current.readyPlayers.contains(player.getUniqueId())
                || !current.onboardingCompletionRequested.add(player.getUniqueId())) {
            return;
        }
        String safeAction = action == null ? "unknown" : action.replaceAll("[^A-Za-z0-9:_-]", "");
        FunnelTelemetry.record(player, FunnelTelemetry.Event.ONBOARDING_COMPLETED,
                "action=" + safeAction);
        current.markOnboardingCompleted(handle);
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

    /** Persists a bounded play-time checkpoint without touching Bukkit off-thread. */
    public static void checkpointActiveSessions(Date checkpointAt) {
        PlayerWrapperListener currentInstance = instance;
        if (currentInstance == null || !currentInstance.acceptingPlayers || checkpointAt == null) {
            return;
        }
        Map<UUID, SessionHandle> sessions = Map.copyOf(currentInstance.activePlayerSessions);
        if (sessions.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(
                () -> currentInstance.checkpointSessions(sessions, checkpointAt),
                currentInstance.persistenceExecutor).exceptionally(error -> {
                    CookieDough.getInstance().getLogger().warning(
                            "Could not checkpoint active player sessions: " + rootMessage(error));
                    return null;
                });
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
        String resumeName = ActivityRegistry.resumeName(player);
        PersistentActivity resumeActivity = resumeName == null
                ? (WorldPolicy.isPersistent(player.getWorld().getName())
                        ? ActivityRegistry.forWorld(player.getWorld().getName()) : null)
                : ActivityRegistry.resumeOwner(player);
        boolean preservePersistentState = resumeName != null || resumeActivity != null;
        if (!preservePersistentState) {
            LobbyManager.teleportPlayerToLobby(cookiePlayer);
        } else {
            cookiePlayer.setState(com.cookiebuild.cookiedough.player.PlayerState.PERSISTENT_MODE);
        }
        String resumeTarget = resumeName != null ? resumeName
                : resumeActivity == null ? null : resumeActivity.name();
        CookiePlayer activeCookiePlayer = cookiePlayer;

        player.sendMessage(ChatColor.GREEN + LocaleManager.getMessage("welcome.message", player.locale(), player.getName()));
        if (resumeTarget == null) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("Cookie Build", NamedTextColor.GOLD),
                    Component.text(LocaleManager.getMessage("lobby.menu.subtitle", player.locale()),
                            NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        }

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
                    player.sendMessage(ChatColor.RED
                            + LocaleManager.getMessage("player.profile_load_failed", player.locale()));
                }
                return;
            }
            SessionHandle current = activePlayerSessions.get(handle.playerId());
            if (current == null || current.generation() != handle.generation() || !player.isOnline()) {
                return;
            }
            readyPlayers.add(handle.playerId());
            CookieDough.getInstance().getGoalTracker().syncPlayer(handle.playerId());
            CookieDough.getInstance().getFriendManager().loadBlocks(player,
                    blocked -> {
                        SessionHandle active = activePlayerSessions.get(handle.playerId());
                        if (active != null && active.generation() == handle.generation() && player.isOnline()) {
                            CookieDough.getInstance().getChatManager()
                                    .replaceBlockedPlayers(handle.playerId(), blocked);
                        }
                    },
                    errorMessage -> CookieDough.getInstance().getLogger().warning(
                            "Could not load persisted blocks for " + player.getName() + ": " + errorMessage));
            LobbyScoreboard.invalidatePlayerCache(handle.playerId());
            boolean resumedPersistent = resumeTarget != null
                    && attemptPersistentResume(player, activeCookiePlayer, resumeTarget);
            boolean awaitingPersistentRecovery = persistentRecovery.isHolding(handle.playerId());
            if (!resumedPersistent && !awaitingPersistentRecovery) showLobbyScoreboard(player);
            boolean newPlayer = newPlayerSessions.remove(handle.sessionId());
            boolean onboardingPending = onboardingPendingSessions.remove(handle.sessionId());
            FunnelTelemetry.record(player, FunnelTelemetry.Event.PLAYER_DATA_READY,
                    "session=" + handle.sessionId() + " new_player=" + newPlayer
                            + " onboarding_pending=" + onboardingPending);
            String queuedActivity = queuedPersistentActivities.remove(handle.playerId());
            boolean quickPlayQueued = queuedQuickPlay.remove(handle.playerId());
            JoinExperiencePlan experience = JoinExperiencePlan.forPlayer(onboardingPending);
            if (!resumedPersistent && !awaitingPersistentRecovery && experience.showOnboarding()
                    && !quickPlayQueued && queuedActivity == null) {
                CookieDough.getInstance().getPlayerHubMenu().openOnboarding(player);
            } else if (!resumedPersistent && !awaitingPersistentRecovery
                    && !onboardingPending && queuedActivity == null) {
                showLobbyHint(player);
            }
            if (experience.showUpdates()) {
                showUnreadChangelog(player, handle);
            }
            if (experience.showAppPromotion()) {
                showMobileAppPromotion(player, handle);
            }
            if (!resumedPersistent && !awaitingPersistentRecovery
                    && queuedActivity == null && Bukkit.getOnlinePlayers().size() <= 1) {
                CookieDough.getInstance().getRallyManager().requestSoloLogin(player);
            }
            if (!resumedPersistent && !awaitingPersistentRecovery && quickPlayQueued) {
                if (onboardingPending) {
                    completeOnboarding(player, "quick");
                }
                CookieDough.getInstance().getLobbyManager().requestQuickPlay(player);
            }
            if (!resumedPersistent && !awaitingPersistentRecovery && queuedActivity != null) {
                if (onboardingPending) {
                    completeOnboarding(player, "activity:" + queuedActivity);
                }
                CookieDough.getInstance().getLobbyManager().requestActivity(player, queuedActivity);
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
                        player.sendMessage(Component.text("www.cookie-build.com/updates", NamedTextColor.AQUA)
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        LocaleManager.getMessage("changelog.join.link_hover", player.locale()))))
                                .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com/updates")));

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

    private void showLobbyHint(Player player) {
        Component hint = Component.text(LocaleManager.getMessage("lobby.menu.action", player.locale()),
                        NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text(
                        LocaleManager.getMessage("lobby.menu.hover", player.locale()))))
                .clickEvent(ClickEvent.runCommand("/menu"));
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            hint = hint.append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(LocaleManager.getMessage("lobby.solo.prompt", player.locale()),
                                    NamedTextColor.GRAY))
                    .append(Component.text(" Discord", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl("https://discord.gg/ajmPnwh9g8")))
                    .append(Component.text(" + ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(LocaleManager.getMessage("lobby.solo.app", player.locale()),
                                    NamedTextColor.LIGHT_PURPLE)
                            .clickEvent(ClickEvent.openUrl("https://www.cookie-build.com/#mobile-app")));
        }
        player.sendMessage(hint);
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
        persistentRecovery.remove(player.getUniqueId());
        queuedQuickPlay.remove(player.getUniqueId());
        queuedPersistentActivities.remove(player.getUniqueId());
        onboardingCompletionRequested.remove(player.getUniqueId());
        CookieDough.getInstance().getPlayerHubMenu().clearPlayer(player.getUniqueId());
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
        boolean onboardingPending = false;
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
                onboardingPending = playerData.getOnboardingCompletedAt() == null;
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
        if (onboardingPending) {
            onboardingPendingSessions.add(handle.sessionId());
        }
    }

    private void markOnboardingCompleted(SessionHandle handle) {
        CompletableFuture.runAsync(() -> {
            try (EntityManager em = HibernateUtil.createEntityManager()) {
                EntityTransaction transaction = em.getTransaction();
                try {
                    transaction.begin();
                    PlayerData playerData = em.find(PlayerData.class, handle.playerId());
                    if (playerData != null && playerData.getOnboardingCompletedAt() == null) {
                        playerData.setOnboardingCompletedAt(new Date());
                    }
                    transaction.commit();
                } catch (RuntimeException error) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                    throw error;
                }
            }
        }, persistenceExecutor).whenComplete((ignored, error) -> {
            if (error != null) {
                onboardingCompletionRequested.remove(handle.playerId());
                CookieDough.getInstance().getLogger().warning("Could not save onboarding completion for "
                        + handle.playerId() + ": " + rootMessage(error));
            }
        });
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
            newPlayerSessions.remove(handle.sessionId());
            onboardingPendingSessions.remove(handle.sessionId());
            initializationFutures.remove(handle.sessionId());
        }, persistenceExecutor);
    }

    private void finalizeSession(SessionHandle handle, Date endTime) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                PlayerSession session = em.find(
                        PlayerSession.class, handle.sessionId(), LockModeType.PESSIMISTIC_WRITE);
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

    private void checkpointSessions(Map<UUID, SessionHandle> handles, Date checkpointAt) {
        try (EntityManager em = HibernateUtil.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                for (SessionHandle handle : handles.values()) {
                    PlayerSession session = em.find(
                            PlayerSession.class, handle.sessionId(), LockModeType.PESSIMISTIC_WRITE);
                    if (session == null || session.getEndTime() != null) {
                        continue;
                    }
                    long duration = Math.max(0L, checkpointAt.getTime() - handle.startTime().getTime());
                    long persisted = session.getDuration() == null ? 0L : session.getDuration();
                    session.setDuration(Math.max(persisted, duration));
                }
                transaction.commit();
            } catch (RuntimeException error) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw error;
            }
        }
    }

    /** Called before Hibernate closes. Waits a bounded time, then performs best-effort direct finalization. */
    public static void shutdownGracefully(Duration timeout) {
        if (instance == null) {
            return;
        }
        instance.acceptingPlayers = false;
        instance.persistentRecoveryTask.cancel();
        instance.persistentRecovery.clear();
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
        if (persistentRecovery.isHolding(event.getPlayer().getUniqueId())) {
            if (event.getTo() != null && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                    || event.getFrom().getBlockY() != event.getTo().getBlockY()
                    || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
                event.setTo(event.getFrom());
            }
            return;
        }
        if (event.getPlayer().getWorld().getName().equals("lobby") && event.getPlayer().getLocation().getY() < 0) {
            LobbyManager.teleportPlayerToLobby(PlayerManager.getPlayer(event.getPlayer()));
        }
    }

    @EventHandler public void onRecoveryDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && persistentRecovery.isHolding(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onRecoveryDrop(PlayerDropItemEvent event) {
        if (persistentRecovery.isHolding(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onRecoveryPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && persistentRecovery.isHolding(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onRecoveryInteract(PlayerInteractEvent event) {
        if (persistentRecovery.isHolding(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onRecoveryBreak(BlockBreakEvent event) {
        if (persistentRecovery.isHolding(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onRecoveryPlace(BlockPlaceEvent event) {
        if (persistentRecovery.isHolding(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler public void onRecoveryInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && persistentRecovery.isHolding(player.getUniqueId())) event.setCancelled(true);
    }

    private boolean attemptPersistentResume(Player player, CookiePlayer cookiePlayer, String activityName) {
        boolean newHold = persistentRecovery.hold(
                player.getUniqueId(), activityName, System.currentTimeMillis());
        PersistentActivityRecovery.Ticket ticket = persistentRecovery.due(System.currentTimeMillis()).stream()
                .filter(candidate -> candidate.playerId().equals(player.getUniqueId()))
                .findFirst().orElse(null);
        if (ticket == null) return false;
        try {
            var admission = ActivityRegistry.enter(activityName, cookiePlayer);
            if (admission.admitted()) {
                persistentRecovery.recovered(ticket);
                player.setInvulnerable(false);
                return true;
            }
        } catch (RuntimeException error) {
            CookieDough.getInstance().getLogger().warning("Could not resume " + activityName + " for "
                    + player.getUniqueId() + ": " + rootMessage(error));
        }
        persistentRecovery.rejected(ticket, System.currentTimeMillis());
        holdPersistentActivity(player, cookiePlayer, activityName, newHold);
        return false;
    }

    private void holdPersistentActivity(Player player, CookiePlayer cookiePlayer,
            String activityName, boolean admissionFailed) {
        boolean firstHold = persistentRecovery.hold(
                player.getUniqueId(), activityName, System.currentTimeMillis());
        if (admissionFailed) {
            persistentRecovery.due(System.currentTimeMillis()).stream()
                    .filter(ticket -> ticket.playerId().equals(player.getUniqueId()))
                    .findFirst().ifPresent(ticket -> persistentRecovery.rejected(
                            ticket, System.currentTimeMillis()));
        }
        cookiePlayer.setState(com.cookiebuild.cookiedough.player.PlayerState.PERSISTENT_MODE);
        hideLobbyScoreboard(player);
        player.closeInventory();
        player.setInvulnerable(true);
        if (firstHold || admissionFailed) {
            player.sendMessage(Component.text(LocaleManager.getMessage(
                    "persistent.resume_unavailable", player.locale()), NamedTextColor.RED));
        }
    }

    private void retryPersistentRecoveries() {
        if (!acceptingPlayers) return;
        long now = System.currentTimeMillis();
        for (PersistentActivityRecovery.Ticket ticket : persistentRecovery.due(now)) {
            Player player = Bukkit.getPlayer(ticket.playerId());
            CookiePlayer cookiePlayer = player == null ? null : PlayerManager.getPlayer(player);
            if (player == null || !player.isOnline() || cookiePlayer == null
                    || !readyPlayers.contains(ticket.playerId())) continue;
            attemptPersistentResume(player, cookiePlayer, ticket.activityName());
        }
    }
}

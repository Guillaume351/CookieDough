package com.cookiebuild.cookiedough.cosmetics;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Native, non-damaging cosmetic effects with verified Geyser mappings. */
public final class CosmeticEffects implements Listener {
    record CachedCosmetics(
            Map<CosmeticSlot, String> selections,
            Map<CosmeticSlot, Instant> selectionExpirations,
            Set<String> entitlements,
            Map<String, Instant> entitlementExpirations, Instant validUntil) {
        CachedCosmetics {
            selections = Map.copyOf(selections);
            selectionExpirations = Map.copyOf(selectionExpirations);
            entitlements = Set.copyOf(entitlements);
            entitlementExpirations = Map.copyOf(entitlementExpirations);
        }

        static CachedCosmetics from(CosmeticService.Inventory inventory, Instant readStartedAt) {
            Set<String> entitled = new HashSet<>();
            inventory.items().stream().filter(CosmeticService.InventoryItem::entitled)
                    .map(item -> item.cosmetic().id()).forEach(entitled::add);
            return new CachedCosmetics(inventory.selections(), inventory.selectionExpirations(),
                    entitled, inventory.entitlementExpirations(), readStartedAt.plusSeconds(15));
        }

        boolean selected(CosmeticSlot slot, String cosmeticId, Instant now) {
            Instant expiresAt = selectionExpirations.get(slot);
            return fresh(now) && cosmeticId.equals(selections.get(slot))
                    && (expiresAt == null || expiresAt.isAfter(now));
        }

        boolean entitled(String cosmeticId, Instant now) {
            Instant expiresAt = entitlementExpirations.get(cosmeticId);
            return fresh(now) && entitlements.contains(cosmeticId)
                    && (expiresAt == null || expiresAt.isAfter(now));
        }

        boolean fresh(Instant now) { return now.isBefore(validUntil); }

        private boolean hasExpired(Instant now) {
            return selectionExpirations.values().stream().anyMatch(expiry -> !expiry.isAfter(now))
                    || entitlementExpirations.values().stream().anyMatch(expiry -> !expiry.isAfter(now));
        }

        private CachedCosmetics withoutExpired(Instant now) {
            EnumMap<CosmeticSlot, String> currentSelections = new EnumMap<>(CosmeticSlot.class);
            EnumMap<CosmeticSlot, Instant> currentSelectionExpirations = new EnumMap<>(CosmeticSlot.class);
            selections.forEach((slot, cosmeticId) -> {
                Instant expiry = selectionExpirations.get(slot);
                if (expiry == null || expiry.isAfter(now)) {
                    currentSelections.put(slot, cosmeticId);
                    if (expiry != null) currentSelectionExpirations.put(slot, expiry);
                }
            });
            Set<String> currentEntitlements = new HashSet<>();
            Map<String, Instant> currentEntitlementExpirations = new HashMap<>();
            entitlements.forEach(cosmeticId -> {
                Instant expiry = entitlementExpirations.get(cosmeticId);
                if (expiry == null || expiry.isAfter(now)) {
                    currentEntitlements.add(cosmeticId);
                    if (expiry != null) currentEntitlementExpirations.put(cosmeticId, expiry);
                }
            });
            return new CachedCosmetics(currentSelections, currentSelectionExpirations,
                    currentEntitlements, currentEntitlementExpirations, validUntil);
        }
    }

    private static final long EMOTE_COOLDOWN_MILLIS = 10_000L;
    private static final long VICTORY_COOLDOWN_MILLIS = 3_000L;
    private static final String FLIGHT_PRESERVE_PERMISSION = "cookiedough.flight.preserve";

    private final CookieDough plugin;
    private final CosmeticService service;
    private final Map<UUID, CachedCosmetics> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastTrailLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEmotes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastVictoryEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Component> originalListNames = new HashMap<>();
    private final Map<UUID, Component> appliedListNames = new HashMap<>();
    private final Set<UUID> managedFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> fallSafety = ConcurrentHashMap.newKeySet();
    private final Set<UUID> joinFlairPlayed = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> refreshesInFlight = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private BukkitTask task;
    private long ticks;
    private volatile boolean running;

    public CosmeticEffects(CookieDough plugin, CosmeticService service) {
        this.plugin = plugin;
        this.service = service;
        service.onChange(playerId -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) refresh(player);
        }));
    }

    public void start() {
        if (task != null) return;
        running = true;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    public void stop() {
        running = false;
        if (task != null) task.cancel();
        task = null;
        for (UUID playerId : Set.copyOf(managedFlight)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) disableManagedFlight(player);
        }
        for (UUID playerId : Set.copyOf(originalListNames.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) restoreListName(player);
        }
        cache.clear();
        lastTrailLocations.clear();
        lastEmotes.clear();
        lastVictoryEffects.clear();
        originalListNames.clear();
        appliedListNames.clear();
        managedFlight.clear();
        fallSafety.clear();
        joinFlairPlayed.clear();
        refreshesInFlight.clear();
        pendingRefreshes.clear();
    }

    public void refresh(Player player) {
        if (!running || player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        UUID request = UUID.randomUUID();
        if (refreshesInFlight.putIfAbsent(playerId, request) != null) {
            pendingRefreshes.add(playerId);
            return;
        }
        Instant readStartedAt = Instant.now();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.Inventory inventory = service.inventory(playerId);
                if (!running) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!refreshesInFlight.remove(playerId, request) || !running || !player.isOnline()) return;
                    cache.put(playerId, CachedCosmetics.from(inventory, readStartedAt));
                    reconcile(player);
                    if (pendingRefreshes.remove(playerId)) refresh(player);
                });
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not refresh cosmetics for " + playerId
                        + ": " + rootMessage(error));
                if (!running) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!refreshesInFlight.remove(playerId, request) || !running || !player.isOnline()) return;
                    cache.remove(playerId);
                    pendingRefreshes.remove(playerId);
                    reconcile(player);
                });
            }
        });
    }

    /** Thread-safe cache read for AsyncChatEvent; expiry is checked per message. */
    public boolean hasActiveSelection(UUID playerId, CosmeticSlot slot, String cosmeticId) {
        CachedCosmetics cosmetics = cache.get(playerId);
        return cosmetics != null && cosmetics.selected(slot, cosmeticId, Instant.now());
    }

    public Component decorateChatDisplayName(UUID playerId, Component displayName) {
        return SupporterTitleFormatter.decorate(displayName, hasActiveSelection(
                playerId, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE));
    }

    /** Preview/player emote hook. Revalidates the entitlement asynchronously. */
    public void playCelebration(Player player) {
        validateThen(player, CosmeticSlot.EMOTE, CosmeticCatalog.COOKIE_CHEER, () -> {
            CookiePlayer wrapped = PlayerManager.getPlayer(player);
            PlayerState state = wrapped == null ? null : wrapped.getState();
            long now = System.currentTimeMillis();
            long last = lastEmotes.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2);
            if (!CosmeticEffectGuard.canUseHubEffect(CosmeticCatalog.COOKIE_CHEER,
                    CosmeticCatalog.COOKIE_CHEER, state, isLobby(player), now, last,
                    EMOTE_COOLDOWN_MILLIS)) {
                player.sendActionBar(Component.text(LocaleManager.getMessage(
                        "cosmetics.emote.unavailable", player.locale()), NamedTextColor.YELLOW));
                return;
            }
            lastEmotes.put(player.getUniqueId(), now);
            player.swingMainHand();
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0, 1.1, 0), 8, 0.45, 0.45, 0.45, 0.0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        });
    }

    /** Non-damaging post-commit winner hook. */
    public void playVictoryEffect(Player player) {
        playVictoryEffect(player, false);
    }

    public void previewVictoryEffect(Player player) {
        playVictoryEffect(player, true);
    }

    private void playVictoryEffect(Player player, boolean preview) {
        validateThen(player, CosmeticSlot.VICTORY_EFFECT, CosmeticCatalog.GOLDEN_COOKIE_BURST, () -> {
            CookiePlayer wrapped = PlayerManager.getPlayer(player);
            PlayerState state = wrapped == null ? null : wrapped.getState();
            long now = System.currentTimeMillis();
            long last = lastVictoryEffects.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2);
            if (preview && !CosmeticEffectGuard.canUseHubEffect(CosmeticCatalog.GOLDEN_COOKIE_BURST,
                    CosmeticCatalog.GOLDEN_COOKIE_BURST, state, isLobby(player), now, last,
                    VICTORY_COOLDOWN_MILLIS)) return;
            if (!CosmeticEffectGuard.canUseVictoryEffect(CosmeticCatalog.GOLDEN_COOKIE_BURST,
                    state, now, last, VICTORY_COOLDOWN_MILLIS)) return;
            lastVictoryEffects.put(player.getUniqueId(), now);
            player.getWorld().spawnParticle(Particle.FIREWORK,
                    player.getLocation().add(0, 1.0, 0), 18, 0.8, 0.8, 0.8, 0.04);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.2f);
        });
    }

    /** Transport guards call this immediately before installing their own temporary permission. */
    public void releaseLobbyFlightOwnership(UUID playerId) {
        managedFlight.remove(playerId);
        fallSafety.remove(playerId);
    }

    /** Called synchronously by the shared game lifecycle before arena teleport. */
    public void disableLobbyFlightBeforeArena(Player player) {
        if (player != null) disableManagedFlight(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        joinFlairPlayed.remove(playerId);
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        disableManagedFlight(player);
        cache.remove(playerId);
        lastTrailLocations.remove(playerId);
        lastEmotes.remove(playerId);
        lastVictoryEffects.remove(playerId);
        originalListNames.remove(playerId);
        appliedListNames.remove(playerId);
        managedFlight.remove(playerId);
        fallSafety.remove(playerId);
        joinFlairPlayed.remove(playerId);
        refreshesInFlight.remove(playerId);
        pendingRefreshes.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isLobby(event.getFrom()) && event.getTo() != null && !isLobby(event.getTo())) {
            disableManagedFlight(event.getPlayer());
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        reconcile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) reconcile(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean armed = fallSafety.remove(player.getUniqueId());
            if (LobbyFlightPolicy.cancelsTransitionFallDamage(armed, isLobby(player))) {
                event.setCancelled(true);
            }
        }
    }

    private void tick() {
        boolean refreshNow = ticks++ % 40L == 0L;
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            CachedCosmetics cosmetics = cache.get(playerId);
            if (cosmetics != null && !cosmetics.fresh(now)) {
                cache.remove(playerId);
                reconcile(player);
            } else if (cosmetics != null && cosmetics.hasExpired(now)) {
                cache.put(playerId, cosmetics.withoutExpired(now));
                reconcile(player);
            }
            // External changes are reconciled within ten seconds; in-process
            // service mutations trigger refresh immediately.
            if (refreshNow) refresh(player);
            reconcileFlight(player);
            applyBadge(player);
            if (player.isOnGround()) fallSafety.remove(playerId);
            tickTrail(player);
        }
    }

    private void reconcile(Player player) {
        applyBadge(player);
        reconcileFlight(player);
        maybePlayJoinFlair(player);
    }

    private void applyBadge(Player player) {
        UUID playerId = player.getUniqueId();
        if (!hasActiveSelection(playerId, CosmeticSlot.BADGE, CosmeticCatalog.SUPPORTER_BADGE)) {
            restoreListName(player);
            return;
        }
        Component current = player.playerListName();
        if (!current.equals(appliedListNames.get(playerId))) originalListNames.put(playerId, current);
        Component decorated = Component.text("★ Supporter ", NamedTextColor.GOLD)
                .append(originalListNames.getOrDefault(playerId, Component.text(player.getName())));
        appliedListNames.put(playerId, decorated);
        if (!decorated.equals(current)) player.playerListName(decorated);
    }

    private void restoreListName(Player player) {
        UUID playerId = player.getUniqueId();
        Component original = originalListNames.remove(playerId);
        Component applied = appliedListNames.remove(playerId);
        if (original != null && applied != null && applied.equals(player.playerListName())) {
            player.playerListName(original);
        }
    }

    private void reconcileFlight(Player player) {
        CookiePlayer wrapped = PlayerManager.getPlayer(player);
        PlayerState state = wrapped == null ? null : wrapped.getState();
        boolean selected = hasActiveSelection(player.getUniqueId(),
                CosmeticSlot.LOBBY_FLIGHT, CosmeticCatalog.LOBBY_FLIGHT);
        if (LobbyFlightPolicy.shouldEnable(selected, isLobby(player), state)) {
            enableManagedFlight(player);
        } else {
            disableManagedFlight(player);
        }
    }

    private void enableManagedFlight(Player player) {
        if (player.getAllowFlight()) return;
        player.setAllowFlight(true);
        managedFlight.add(player.getUniqueId());
    }

    private void disableManagedFlight(Player player) {
        UUID playerId = player.getUniqueId();
        if (!managedFlight.remove(playerId)) return;
        if (LobbyFlightPolicy.preservesIndependentFlight(
                player.getGameMode(), player.hasPermission(FLIGHT_PRESERVE_PERMISSION))) return;
        if (player.isFlying()) player.setFlying(false);
        player.setAllowFlight(false);
        player.setFallDistance(0);
        fallSafety.add(playerId);
    }

    private void maybePlayJoinFlair(Player player) {
        CachedCosmetics cosmetics = cache.get(player.getUniqueId());
        boolean entitled = cosmetics != null && cosmetics.entitled(
                CosmeticCatalog.SUPPORTER_JOIN_FLAIR, Instant.now());
        if (!SupporterJoinFlairPolicy.shouldPlay(entitled, isLobby(player),
                joinFlairPlayed.contains(player.getUniqueId()))) return;
        joinFlairPlayed.add(player.getUniqueId());
        player.spawnParticle(Particle.END_ROD,
                player.getLocation().add(0, 1.0, 0), 6, 0.35, 0.55, 0.35, 0.01);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.35f);
    }

    private void tickTrail(Player player) {
        String trail = selected(player.getUniqueId(), CosmeticSlot.HUB_TRAIL);
        CookiePlayer wrapped = PlayerManager.getPlayer(player);
        if ((!CosmeticCatalog.COOKIE_CRUMB_TRAIL.equals(trail)
                && !CosmeticCatalog.COOKIE_SPARKLE_TRAIL.equals(trail))
                || wrapped == null || wrapped.getState() != PlayerState.LOBBY || !isLobby(player)) {
            lastTrailLocations.remove(player.getUniqueId());
            return;
        }
        Location current = player.getLocation();
        Location previous = lastTrailLocations.put(player.getUniqueId(), current.clone());
        if (previous == null || !previous.getWorld().equals(current.getWorld())
                || previous.distanceSquared(current) < 0.04) return;
        player.getWorld().spawnParticle(CosmeticCatalog.COOKIE_SPARKLE_TRAIL.equals(trail)
                        ? Particle.END_ROD : Particle.FALLING_HONEY,
                current.clone().add(0, 0.15, 0), 1, 0.08, 0.03, 0.08, 0.0);
    }

    private String selected(UUID playerId, CosmeticSlot slot) {
        CachedCosmetics cosmetics = cache.get(playerId);
        if (cosmetics == null) return null;
        String cosmeticId = cosmetics.selections().get(slot);
        return cosmeticId != null && cosmetics.selected(slot, cosmeticId, Instant.now()) ? cosmeticId : null;
    }

    private void validateThen(Player player, CosmeticSlot slot, String cosmeticId, Runnable effect) {
        if (!running || player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                CosmeticService.Inventory inventory = service.inventory(playerId);
                if (!running) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!running || !player.isOnline()) return;
                    if (CosmeticEffectAuthorization.isSelected(inventory, slot, cosmeticId)) effect.run();
                });
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not authorize cosmetic effect for " + playerId
                        + ": " + rootMessage(error));
            }
        });
    }

    private static boolean isLobby(Player player) {
        return player.getWorld() != null && "lobby".equals(player.getWorld().getName());
    }

    private static boolean isLobby(Location location) {
        return location.getWorld() != null && "lobby".equals(location.getWorld().getName());
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}

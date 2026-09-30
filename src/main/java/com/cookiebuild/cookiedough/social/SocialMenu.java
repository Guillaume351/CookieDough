package com.cookiebuild.cookiedough.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.activity.ActivityRegistry;
import com.cookiebuild.cookiedough.activity.PersistentActivity;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.retention.FriendManager;
import com.cookiebuild.cookiedough.retention.FriendRepository;
import com.cookiebuild.cookiedough.retention.PartyManager;
import com.cookiebuild.cookiedough.social.SocialMenuAction.Kind;
import com.cookiebuild.cookiedough.social.SocialView.Presence;
import com.cookiebuild.cookiedough.ui.BedrockButtonText;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockFormSupport;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;
import com.cookiebuild.cookiedough.ui.MainThreadPlayerAction;
import com.cookiebuild.cookiedough.ui.MenuLore;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * "Friends & party" menu: a chest inventory for Java and a Cumulus form for
 * Bedrock, so every friend and party action works without typing a command.
 * Durable data is loaded off the main thread by {@link FriendManager} and
 * {@link PartyManager}; rendering and live presence run on the main thread.
 */
public final class SocialMenu implements Listener {
    private static final long CACHE_MILLIS = 20_000L;
    private static final int MAX_CONTENT_SLOTS = 45;

    private final CookieDough plugin;
    private final FriendManager friends;
    private final PartyManager parties;
    private final NamespacedKey actionKey;
    private final BedrockMenuSessionRegistry bedrockSessions = new BedrockMenuSessionRegistry();
    /** Main-thread only. */
    private final Map<UUID, Session> sessions = new HashMap<>();

    private record Data(FriendManager.MenuSnapshot snapshot, List<UUID> partyInviteLeaders, long loadedAt) {
    }

    private static final class Session {
        private UUID viewId;
        private UUID loadId;
        private Data data;
    }

    public SocialMenu(CookieDough plugin, FriendManager friends, PartyManager parties) {
        this.plugin = plugin;
        this.friends = friends;
        this.parties = parties;
        this.actionKey = new NamespacedKey(plugin, "social_action");
    }

    /** Opens the menu home (friends, requests, add a friend, party). Main thread. */
    public void open(Player player) {
        load(player, SocialPageId.HOME, "", null);
    }

    /** Opens the menu directly on the party screen. Main thread. */
    public void openParty(Player player) {
        load(player, SocialPageId.PARTY, "", null);
    }

    private void load(Player player, SocialPageId page, String context, String notice) {
        UUID playerId = player.getUniqueId();
        UUID loadId = UUID.randomUUID();
        Session session = sessions.computeIfAbsent(playerId, ignored -> new Session());
        session.loadId = loadId;
        friends.loadMenu(player, snapshot -> parties.loadPendingInvites(player, leaders -> {
            Session current = sessions.get(playerId);
            if (!player.isOnline() || current == null || !loadId.equals(current.loadId)) return;
            current.data = new Data(snapshot, leaders, System.currentTimeMillis());
            SocialPageId target = page == SocialPageId.REQUESTS && snapshot.friends().incoming().isEmpty()
                    ? SocialPageId.HOME : page;
            render(player, target, context, notice, current);
        }), error -> {
            if (player.isOnline()) player.sendMessage(ChatColor.RED + error);
        });
    }

    private void navigate(Player player, SocialPageId page, String context) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.data == null
                || System.currentTimeMillis() - session.data.loadedAt() > CACHE_MILLIS) {
            load(player, page, context, null);
        } else {
            render(player, page, context, null, session);
        }
    }

    private void render(Player player, SocialPageId page, String context, String notice, Session session) {
        SocialView view = view(player, session.data);
        SocialPage built = SocialMenuModel.build(page, context, view, notice,
                (key, args) -> message(player, key, args));
        UUID viewId = UUID.randomUUID();
        session.viewId = viewId;
        if (view.bedrock() && openBedrock(player, built, viewId)) return;
        player.openInventory(javaInventory(built, viewId));
    }

    // ---------------------------------------------------------------- live state

    private SocialView view(Player player, Data data) {
        UUID playerId = player.getUniqueId();
        FriendRepository.Snapshot graph = data.snapshot().friends();
        List<SocialView.Friend> friendViews = new ArrayList<>();
        for (FriendRepository.Friend friend : graph.friends()) {
            Player online = friend.online() ? Bukkit.getPlayerExact(friend.name()) : null;
            if (!friend.online()) {
                friendViews.add(new SocialView.Friend(friend.name(), Presence.OFFLINE, "", false));
            } else if (online == null || !player.canSee(online)) {
                friendViews.add(new SocialView.Friend(friend.name(), Presence.ELSEWHERE, "", false));
            } else {
                Activity activity = activity(online);
                friendViews.add(new SocialView.Friend(online.getName(), activity.presence(), activity.name(),
                        parties.arePartyMembers(playerId, online.getUniqueId())));
            }
        }
        UUID leaderId = parties.getLeaderId(playerId);
        SocialView.Party party = null;
        if (leaderId != null) {
            List<SocialView.Member> members = parties.getMembers(playerId).stream()
                    .map(memberId -> {
                        Player member = Bukkit.getPlayer(memberId);
                        return new SocialView.Member(parties.playerName(memberId),
                                member != null && member.isOnline(), memberId.equals(leaderId));
                    }).toList();
            party = new SocialView.Party(parties.playerName(leaderId), leaderId.equals(playerId), members,
                    PartyManager.maxPartySize());
        }
        List<SocialView.PartyInvite> invites = data.partyInviteLeaders().stream()
                .filter(id -> !id.equals(leaderId) && !id.equals(playerId))
                .map(id -> new SocialView.PartyInvite(id, parties.playerName(id)))
                .toList();
        Set<UUID> blocked = data.snapshot().blocked();
        List<String> online = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(playerId))
                .filter(player::canSee)
                .filter(other -> !blocked.contains(other.getUniqueId()))
                .map(Player::getName)
                .toList();
        CookiePlayer self = PlayerManager.getPlayer(player);
        boolean canQueue = self != null && (self.getState() == PlayerState.LOBBY
                || self.getState() == PlayerState.PERSISTENT_MODE);
        return new SocialView(player.getName(), BedrockFormSupport.isBedrock(player), canQueue,
                activity(player).name(), friendViews, graph.incoming(), graph.outgoing(), party, invites, online);
    }

    private record Activity(Presence presence, String name) {
    }

    /** Cheap, read-only view of what an online player is doing on this server. */
    private static Activity activity(Player player) {
        GameManager.QueueIntent intent = GameManager.getQueueIntent(player.getUniqueId());
        if (intent != null && intent.gameName() != null) return new Activity(Presence.QUEUED, intent.gameName());
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null) return new Activity(Presence.ONLINE, "");
        return switch (cookiePlayer.getState()) {
            case LOBBY -> new Activity(Presence.LOBBY, "");
            case QUEUED -> new Activity(Presence.QUEUED, gameName(cookiePlayer));
            case IN_GAME -> new Activity(Presence.PLAYING, gameName(cookiePlayer));
            case SPECTATING -> new Activity(Presence.SPECTATING, gameName(cookiePlayer));
            case PERSISTENT_MODE -> {
                PersistentActivity owner = ActivityRegistry.owner(player.getUniqueId());
                yield new Activity(Presence.ACTIVITY, owner == null ? "" : owner.name());
            }
            case OFFLINE -> new Activity(Presence.ONLINE, "");
        };
    }

    private static String gameName(CookiePlayer player) {
        Game game = GameManager.getGameOfPlayer(player);
        return game == null ? "" : game.getGameName();
    }

    // ---------------------------------------------------------------- actions

    private void dispatch(Player player, SocialMenuAction action, boolean bedrock) {
        if (action.navigation()) {
            navigate(player, action.kind().page(), action.argument());
            return;
        }
        String name = action.argument();
        switch (action.kind()) {
            case CLOSE -> {
                if (!bedrock) player.closeInventory();
            }
            case ACCEPT -> mutate(player, bedrock, SocialPageId.REQUESTS, "",
                    done -> friends.accept(player, name, done));
            case DENY -> mutate(player, bedrock, SocialPageId.REQUESTS, "",
                    done -> friends.deny(player, name, done));
            case SEND_REQUEST -> mutate(player, bedrock, SocialPageId.ADD, "",
                    done -> friends.request(player, name, done));
            case REMOVE -> mutate(player, bedrock, SocialPageId.FRIENDS, "",
                    done -> friends.remove(player, name, done));
            case INVITE -> {
                Player target = Bukkit.getPlayerExact(name);
                if (target == null || !player.canSee(target)) {
                    String notice = message(player, "party.player_offline");
                    player.sendMessage(ChatColor.YELLOW + notice);
                    load(player, SocialPageId.PARTY, "", notice);
                } else {
                    mutate(player, bedrock, SocialPageId.PARTY, "", done -> parties.invite(player, target, done));
                }
            }
            case JOIN_PARTY -> {
                UUID leaderId = action.uuidArgument();
                mutate(player, bedrock, SocialPageId.PARTY, "",
                        done -> parties.join(player, leaderId, parties.playerName(leaderId), done));
            }
            case LEAVE -> mutate(player, bedrock, SocialPageId.PARTY, "", done -> parties.leave(player, done));
            case JOIN_QUEUE -> followFriend(player, name, bedrock);
            case ADD_BY_NAME -> {
                if (bedrock) openNameForm(player);
            }
            case NOOP -> { }
            default -> { }
        }
    }

    /** Uses the same lobby entry point as the game selector, so party rules and queue checks apply. */
    private void followFriend(Player player, String name, boolean bedrock) {
        Player friend = Bukkit.getPlayerExact(name);
        Activity activity = friend == null || !player.canSee(friend) ? null : activity(friend);
        boolean joinable = activity != null && !activity.name().isBlank()
                && (activity.presence() == Presence.QUEUED || activity.presence() == Presence.PLAYING
                        || activity.presence() == Presence.ACTIVITY);
        if (!joinable) {
            String notice = message(player, "social.join.gone", name);
            player.sendMessage(ChatColor.YELLOW + notice);
            load(player, SocialPageId.FRIEND, name, notice);
            return;
        }
        invalidate(player);
        if (!bedrock) player.closeInventory();
        player.sendMessage(ChatColor.GREEN + message(player, "social.join.started", activity.name(), friend.getName()));
        plugin.getLobbyManager().requestSelectedActivity(player, activity.name());
    }

    private void mutate(Player player, boolean bedrock, SocialPageId after, String context,
            Consumer<Consumer<String>> operation) {
        invalidate(player);
        if (!bedrock) player.closeInventory();
        operation.accept(result -> {
            if (!player.isOnline()) return;
            player.sendMessage(ChatColor.YELLOW + result);
            load(player, after, context, result);
        });
    }

    private void invalidate(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) session.viewId = null;
        bedrockSessions.invalidate(player.getUniqueId());
    }

    // ---------------------------------------------------------------- Java

    private Inventory javaInventory(SocialPage page, UUID viewId) {
        List<SocialPage.Entry> body = page.entries().stream().filter(entry -> !entry.footer()).toList();
        List<SocialPage.Entry> footer = page.entries().stream().filter(SocialPage.Entry::footer).toList();
        int bodyRows = Math.max(1, Math.min(5, (body.size() + 8) / 9));
        SocialHolder holder = new SocialHolder(viewId, (bodyRows + 1) * 9,
                Component.text(page.title(), NamedTextColor.GOLD));
        Inventory inventory = holder.getInventory();
        for (int index = 0; index < body.size() && index < MAX_CONTENT_SLOTS; index++) {
            inventory.setItem(index, item(body.get(index)));
        }
        int base = bodyRows * 9;
        int[] footerSlots = { base, base + 8, base + 1, base + 7 };
        for (int index = 0; index < footer.size() && index < footerSlots.length; index++) {
            inventory.setItem(footerSlots[index], item(footer.get(index)));
        }
        if (!page.content().isEmpty()) {
            ItemStack info = new ItemStack(Material.OAK_SIGN);
            ItemMeta meta = info.getItemMeta();
            meta.displayName(Component.text(page.content().getFirst(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(page.content().stream().skip(1)
                    .map(line -> Component.text(line, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
                    .toList());
            info.setItemMeta(meta);
            inventory.setItem(base + 4, info);
        }
        return inventory;
    }

    private ItemStack item(SocialPage.Entry entry) {
        Player head = entry.headName().isEmpty() ? null : Bukkit.getPlayerExact(entry.headName());
        ItemStack item = new ItemStack(head != null ? Material.PLAYER_HEAD : material(entry.icon()));
        ItemMeta meta = item.getItemMeta();
        if (head != null && meta instanceof SkullMeta skull) skull.setOwningPlayer(head);
        NamedTextColor color = entry.icon() == SocialPage.Icon.FRIEND_OFFLINE ? NamedTextColor.GRAY
                : entry.decorative() ? NamedTextColor.YELLOW : NamedTextColor.GOLD;
        meta.displayName(Component.text(entry.label(), color).decoration(TextDecoration.ITALIC, false));
        if (!entry.detail().isBlank()) meta.lore(List.of(MenuLore.detail(entry.detail())));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, entry.action().encode());
        item.setItemMeta(meta);
        return item;
    }

    private static Material material(SocialPage.Icon icon) {
        return switch (icon) {
            case FRIEND_ONLINE, PLAYER, MEMBER -> Material.PLAYER_HEAD;
            case FRIEND_OFFLINE -> Material.SKELETON_SKULL;
            case REQUEST -> Material.WRITABLE_BOOK;
            case ADD -> Material.EMERALD;
            case TYPE_NAME -> Material.NAME_TAG;
            case PARTY, PARTY_INVITE, INVITE -> Material.CAKE;
            case JOIN_QUEUE -> Material.COMPASS;
            case ACCEPT -> Material.LIME_DYE;
            case DENY -> Material.RED_DYE;
            case REMOVE, CLOSE -> Material.BARRIER;
            case LEAVE -> Material.OAK_DOOR;
            case FRIENDS -> Material.BOOK;
            case MORE -> Material.PAPER;
            case BACK -> Material.ARROW;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SocialHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !holder.viewId.equals(session.viewId)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String encoded = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        SocialMenuAction.parse(encoded).ifPresent(action -> dispatch(player, action, false));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SocialHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        bedrockSessions.invalidate(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- Bedrock

    private boolean openBedrock(Player player, SocialPage page, UUID viewId) {
        try {
            UUID playerId = player.getUniqueId();
            String scope = "social:" + viewId;
            UUID nonce = bedrockSessions.issue(playerId, scope);
            SimpleForm.Builder builder = SimpleForm.builder()
                    .title("§l§6" + page.title())
                    .content(String.join("\n", page.content()));
            List<SocialMenuAction> actions = new ArrayList<>();
            for (SocialPage.Entry entry : page.bedrockButtons()) {
                BedrockFormImages.button(builder, BedrockButtonText.format(entry.label(), entry.detail()),
                        bedrockImage(entry.icon()));
                actions.add(entry.action());
            }
            builder.validResultHandler(response -> {
                int index = response.clickedButtonId();
                if (index < 0 || index >= actions.size()) return;
                SocialMenuAction action = actions.get(index);
                MainThreadPlayerAction.dispatch(plugin, player, () -> {
                    Session session = sessions.get(playerId);
                    if (bedrockSessions.consume(playerId, nonce, scope)
                            && session != null && viewId.equals(session.viewId)) {
                        dispatch(player, action, true);
                    }
                });
            });
            builder.closedOrInvalidResultHandler(() -> bedrockSessions.invalidate(playerId, nonce, scope));
            if (BedrockFormSupport.send(player, builder.build())) return true;
            bedrockSessions.invalidate(playerId, nonce, scope);
            return false;
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Could not open Bedrock social menu for " + player.getName() + ": "
                    + error.getMessage());
            return false;
        }
    }

    /** Secondary Bedrock path for a player who is not listed (e.g. offline): one text field. */
    private void openNameForm(Player player) {
        try {
            UUID playerId = player.getUniqueId();
            String scope = "social:name";
            UUID nonce = bedrockSessions.issue(playerId, scope);
            CustomForm form = CustomForm.builder()
                    .title("§l§6" + message(player, "social.add.by_name.title"))
                    .input(message(player, "social.add.by_name.input"), message(player, "social.add.by_name.placeholder"))
                    .validResultHandler(response -> {
                        String typed = response.asInput(0);
                        MainThreadPlayerAction.dispatch(plugin, player, () -> {
                            if (!bedrockSessions.consume(playerId, nonce, scope)) return;
                            String name = typed == null ? "" : typed.trim();
                            if (name.isEmpty()) {
                                navigate(player, SocialPageId.ADD, "");
                            } else {
                                mutate(player, true, SocialPageId.ADD, "", done -> friends.request(player, name, done));
                            }
                        });
                    })
                    .closedOrInvalidResultHandler(() -> bedrockSessions.invalidate(playerId, nonce, scope))
                    .build();
            if (!BedrockFormSupport.send(player, form)) bedrockSessions.invalidate(playerId, nonce, scope);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Could not open Bedrock friend name form for " + player.getName() + ": "
                    + error.getMessage());
        }
    }

    private static String bedrockImage(SocialPage.Icon icon) {
        return switch (icon) {
            case FRIEND_ONLINE, FRIEND_OFFLINE, PLAYER, MEMBER, REQUEST, ADD, TYPE_NAME, FRIENDS -> "actions/friends";
            case PARTY, PARTY_INVITE, INVITE -> "actions/party";
            case JOIN_QUEUE -> "actions/quick_play";
            case ACCEPT -> "actions/join";
            case BACK -> "actions/back";
            case CLOSE, DENY, REMOVE, LEAVE -> "actions/close";
            case MORE -> "actions/app";
        };
    }

    private static String message(Player player, String key, Object... args) {
        return LocaleManager.getMessage(key, player.locale(), args);
    }

    private static final class SocialHolder implements InventoryHolder {
        private final UUID viewId;
        private final Inventory inventory;

        private SocialHolder(UUID viewId, int size, Component title) {
            this.viewId = viewId;
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

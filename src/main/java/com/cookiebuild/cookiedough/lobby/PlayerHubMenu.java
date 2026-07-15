package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.retention.FriendManager;
import com.cookiebuild.cookiedough.retention.PlayerGoalTracker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One discoverable lobby menu, rendered natively for Java and Bedrock players. */
public final class PlayerHubMenu implements Listener {
    private static final List<String> GAMES = List.of(
            "MicroBattles", "Pitchout", "SkyWars", "BuildBattles", "TurfWars");

    private final CookieDough plugin;
    private final LobbyManager lobby;
    private final PlayerGoalTracker goals;
    private final FriendManager friends;
    private final NamespacedKey actionKey;

    public PlayerHubMenu(CookieDough plugin, LobbyManager lobby, PlayerGoalTracker goals, FriendManager friends) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.goals = goals;
        this.friends = friends;
        this.actionKey = new NamespacedKey(plugin, "hub_menu_action");
    }

    public void open(Player player) {
        if (openBedrock(player, MenuPage.MAIN)) return;
        player.openInventory(mainInventory());
    }

    private Inventory mainInventory() {
        MenuHolder holder = new MenuHolder(MenuPage.MAIN, 27, Component.text("Cookie Build Menu", NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(10, item(Material.NETHER_STAR, "Quick Play", "quick",
                "Join the game closest to starting"));
        inventory.setItem(12, item(Material.GRASS_BLOCK, "Choose a game", "games",
                "MicroBattles, Pitchout, SkyWars, BuildBattles or TurfWars"));
        inventory.setItem(14, item(Material.EXPERIENCE_BOTTLE, "Daily & weekly quests", "goals",
                "Track objectives, rewards and achievements"));
        inventory.setItem(16, item(Material.PLAYER_HEAD, "Friends", "friends",
                "Requests, online friends and /friend commands"));
        inventory.setItem(19, item(Material.COOKIE, "Party", "party", "Play together with your group"));
        inventory.setItem(21, item(Material.CLOCK, "Community events", "events", "Next session and reminders"));
        inventory.setItem(23, item(Material.ENDER_EYE, "Cookie Build app", "app",
                "Link the app and receive player calls"));
        inventory.setItem(25, item(Material.BOOK, "Help", "help", "Show the useful commands"));
        return inventory;
    }

    private Inventory gamesInventory() {
        MenuHolder holder = new MenuHolder(MenuPage.GAMES, 27, Component.text("Choose a game", NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        Material[] icons = {
                Material.RED_CONCRETE, Material.SLIME_BALL, Material.ENDER_EYE,
                Material.CRAFTING_TABLE, Material.BOW
        };
        int[] slots = { 9, 11, 13, 15, 17 };
        for (int index = 0; index < GAMES.size(); index++) {
            String game = GAMES.get(index);
            inventory.setItem(slots[index], item(icons[index], game, "game:" + game, "Join an open waiting lobby"));
        }
        inventory.setItem(22, item(Material.ARROW, "Back", "back", "Return to the Cookie Build menu"));
        return inventory;
    }

    private Inventory goalsInventory(Player player) {
        PlayerGoalTracker.GoalView view = goals.view(player.getUniqueId());
        MenuHolder holder = new MenuHolder(MenuPage.GOALS, 27,
                Component.text("Daily & weekly quests", NamedTextColor.GOLD));
        Inventory inventory = holder.inventory();
        inventory.setItem(11, item(view.dailyComplete() ? Material.LIME_DYE : Material.SUNFLOWER,
                "Daily quests", "noop",
                progress("Play a match", view.dailyMatches(), 1),
                progress("Win a match", view.dailyWins(), 1),
                "Resets every day (UTC)"));
        inventory.setItem(13, item(view.weeklyComplete() ? Material.LIME_DYE : Material.DIAMOND,
                "Weekly quests", "noop",
                progress("Play matches", view.weeklyMatches(), 3),
                progress("Win a match", view.weeklyWins(), 1),
                progress("Eliminations", view.weeklyEliminations(), 10),
                "Resets every Monday (UTC)"));
        inventory.setItem(15, item(Material.ENCHANTED_BOOK, "Achievements", "noop",
                view.achievements() + " unlocked", "Complete games and quests to earn more"));
        inventory.setItem(22, item(Material.ARROW, "Back", "back", "Return to the Cookie Build menu"));
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action != null) dispatch(player, action);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void dispatch(Player player, String action) {
        if (action.startsWith("game:")) {
            player.closeInventory();
            lobby.requestGame(player, action.substring("game:".length()));
            return;
        }
        switch (action) {
            case "quick" -> {
                player.closeInventory();
                lobby.requestQuickPlay(player);
            }
            case "games" -> openPage(player, MenuPage.GAMES);
            case "goals" -> openPage(player, MenuPage.GOALS);
            case "friends" -> {
                player.closeInventory();
                friends.describe(player, message -> player.sendMessage(ChatColor.YELLOW + message));
            }
            case "party" -> run(player, "party list");
            case "events" -> run(player, "events");
            case "app" -> run(player, "app status");
            case "help" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "Useful commands: " + ChatColor.YELLOW
                        + "/menu, /friend, /party, /goals, /events, /app, /quickplay, /lobby");
            }
            case "back" -> openPage(player, MenuPage.MAIN);
            default -> {
                // Decorative goal entries deliberately have no action.
            }
        }
    }

    private void run(Player player, String command) {
        player.closeInventory();
        player.performCommand(command);
    }

    private void openPage(Player player, MenuPage page) {
        if (openBedrock(player, page)) return;
        player.openInventory(switch (page) {
            case MAIN -> mainInventory();
            case GAMES -> gamesInventory();
            case GOALS -> goalsInventory(player);
        });
    }

    private boolean openBedrock(Player player, MenuPage page) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false;
        try {
            FloodgatePlayer floodgate = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgate == null) return false;
            PlayerGoalTracker.GoalView view = goals.view(player.getUniqueId());
            SimpleForm.Builder builder = SimpleForm.builder();
            List<String> actions = new ArrayList<>();
            switch (page) {
                case MAIN -> {
                    builder.title("§l§6Cookie Build Menu")
                            .content("§7Play, track your quests and find other players.");
                    button(builder, actions, "§a§lQuick Play\n§7Closest game to starting", "quick");
                    button(builder, actions, "§6§lChoose a game", "games");
                    button(builder, actions, "§b§lDaily & weekly quests", "goals");
                    button(builder, actions, "§d§lFriends", "friends");
                    button(builder, actions, "§e§lParty", "party");
                    button(builder, actions, "§9§lCommunity events", "events");
                    button(builder, actions, "§5§lCookie Build app\n§7Player-call notifications", "app");
                    button(builder, actions, "§f§lHelp & commands", "help");
                }
                case GAMES -> {
                    builder.title("§l§6Choose a game").content("§7Join an open waiting lobby.");
                    for (String game : GAMES) button(builder, actions, "§f§l" + game, "game:" + game);
                    button(builder, actions, "§7Back", "back");
                }
                case GOALS -> {
                    builder.title("§l§6Daily & weekly quests")
                            .content("§eDaily\n§fMatch " + view.dailyMatches() + "/1  •  Win " + view.dailyWins()
                                    + "/1\n\n§bWeekly\n§fMatches " + view.weeklyMatches() + "/3  •  Wins "
                                    + view.weeklyWins() + "/1  •  Eliminations " + view.weeklyEliminations()
                                    + "/10\n\n§dAchievements: §f" + view.achievements());
                    button(builder, actions, "§7Back", "back");
                }
            }
            builder.validResultHandler(response -> {
                int index = response.getClickedButtonId();
                if (index >= 0 && index < actions.size()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) dispatch(player, actions.get(index));
                    });
                }
            });
            floodgate.sendForm(builder.build());
            return true;
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Could not open Bedrock lobby menu for " + player.getName() + ": "
                    + error.getMessage());
            return false;
        }
    }

    private static void button(SimpleForm.Builder builder, List<String> actions, String label, String action) {
        builder.button(label);
        actions.add(action);
    }

    private ItemStack item(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(java.util.Arrays.stream(lore).map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private static String progress(String label, int current, int target) {
        return (current >= target ? "✓ " : "• ") + label + ": " + current + "/" + target;
    }

    private enum MenuPage {
        MAIN,
        GAMES,
        GOALS
    }

    private static final class MenuHolder implements InventoryHolder {
        private final Inventory inventory;

        private MenuHolder(MenuPage page, int size, Component title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private Inventory inventory() {
            return inventory;
        }
    }
}

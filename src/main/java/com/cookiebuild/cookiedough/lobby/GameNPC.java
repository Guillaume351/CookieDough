package com.cookiebuild.cookiedough.lobby;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.GameStatus;
import com.cookiebuild.cookiedough.listener.NPCReloadListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;

public class GameNPC {
    private final String gameName;
    private static NPCReloadListener reloadListener;
    private final CookieDough plugin;
    private final Location location;
    private Zombie npc;

    public GameNPC(String gameName, Location location, CookieDough plugin) {
        this.gameName = gameName;
        this.plugin = plugin;
        this.location = location;

        if (reloadListener != null) {
            reloadListener.registerNPC(gameName, location);
        }

        spawnNPC();
    }

    public static void setReloadListener(NPCReloadListener listener) {
        reloadListener = listener;
    }

    private void spawnNPC() {
        this.npc = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);

        // Customize the NPC
        this.npc.setBaby(false);
        this.npc.setAI(false);
        this.npc.setPersistent(true);

        npc.getPersistentDataContainer().set(new NamespacedKey(plugin, gameName), PersistentDataType.BYTE, (byte) 1);

        // Give the NPC a sword
        this.npc.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        // Start name refresh task
        startNameRefreshTask();
    }

    private void startNameRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateNPCName();
            }
        }.runTaskTimer(plugin, 0, 20); // Update every second
    }

    private void updateNPCName() {
        GameStatus game = GameManager.getGameByName(gameName);
        if (game != null) {
            int playerCount = game.getPlayerCount();
            int maxPlayers = game.getCapacity();
            String state = game.getState().toString();

            String nameFormat = ChatColor.GOLD + "" + ChatColor.BOLD + "%s " +
                    ChatColor.RESET + ChatColor.AQUA + "[" +
                    ChatColor.YELLOW + "%d" + ChatColor.GOLD + "/" + ChatColor.YELLOW + "%d" +
                    ChatColor.AQUA + "] " +
                    ChatColor.GREEN + "%s";

            String customName = String.format(nameFormat, gameName, playerCount, maxPlayers, state);
            npc.setCustomName(customName);
            npc.setCustomNameVisible(true);
        } else {
            npc.setCustomName(ChatColor.RED + gameName + " (Unavailable)");
            npc.setCustomNameVisible(true);
        }
    }

    public void interactWithPlayer(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        GameStatus game = GameManager.getGameByName(gameName);

        if (game != null && game.getState() == GameState.OPEN) {
            if (game.addPlayerToAvailableTeam(cookiePlayer)) {
                player.sendMessage(ChatColor.GREEN + "You've joined a " + gameName + " game!");
            } else {
                player.sendMessage(ChatColor.RED + "No available " + gameName + " games. Please wait.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Error: Game not found.");
        }
    }

    public Zombie getNPC() {
        return npc;
    }

    public String getGameName() {
        return gameName;
    }

    public Location getLocation() {
        return location;
    }
}
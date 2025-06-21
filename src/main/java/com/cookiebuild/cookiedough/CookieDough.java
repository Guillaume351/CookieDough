package com.cookiebuild.cookiedough;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.hibernate.SessionFactory;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.commands.LobbyCommand;
import com.cookiebuild.cookiedough.commands.MessageTestCommand;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.listener.NPCReloadListener;
import com.cookiebuild.cookiedough.listener.PlayerChatListener;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.WorldEventListener;
import com.cookiebuild.cookiedough.lobby.GameNPC;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.scheduler.MessageScheduler;
import com.cookiebuild.cookiedough.service.MinigameStatsService;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.cookiedough.utils.RabbitMQInitializer;

public final class CookieDough extends JavaPlugin {
    static CookieDough instance;
    public static SessionFactory sessionFactory;
    private static LobbyManager lobbyManager;
    private static PlayerStatsService playerStatsService;
    private static LocaleManager localeManager;
    private static MessageScheduler messageScheduler;

    public static CookieDough getInstance() {
        return instance;
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new BaseEventBlocker(), this);
        getServer().getPluginManager().registerEvents(new PlayerWrapperListener(), this);
        getServer().getPluginManager().registerEvents(new WorldEventListener(), this);

        ChatManager chatManager = new ChatManager();
        getServer().getPluginManager().registerEvents(new PlayerChatListener(chatManager), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
    }

    public static LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public static PlayerStatsService getPlayerStatsService() {
        if (playerStatsService == null) {
            playerStatsService = new PlayerStatsService(getSessionFactory().createEntityManager());
        }
        return playerStatsService;
    }

    public static MinigameStatsService createMinigameStatsService() {
        return new MinigameStatsService(getSessionFactory().createEntityManager());
    }

    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            sessionFactory = HibernateUtil.buildSessionFactory();
        }
        return sessionFactory;
    }

    public static LocaleManager getLocaleManager() {
        if (localeManager == null) {
            localeManager = new LocaleManager();
        }
        return localeManager;
    }

    public static MessageScheduler getMessageScheduler() {
        return messageScheduler;
    }

    @Override
    public void onEnable() {
        this.getLogger().info("Enabling CookieDough");
        // Affect instance
        instance = this;

        // Initialize RabbitMQ
        RabbitMQInitializer.initialize();

        // Initialize lobby manager
        List<Location> gameSigns = new ArrayList<>();
        gameSigns.add(getServer().getWorld("lobby").getBlockAt(0, 8, 12).getLocation());
        gameSigns.add(getServer().getWorld("lobby").getBlockAt(0, 9, 12).getLocation());
        this.getLogger().info("Game signs: " + gameSigns);

        lobbyManager = new LobbyManager(this, gameSigns);

        World lobbyWorld = getServer().getWorld("lobby");

        Location npcLocation = new Location(lobbyWorld, 0.5, 8, 12.5);
        npcLocation.setYaw(180);
        lobbyManager.addGameNpc("MicroBattles", npcLocation);

        Location pitchoutLocation = new Location(lobbyWorld, 0.5, 8, -11.5);
        lobbyManager.addGameNpc("Pitchout", pitchoutLocation);

        // register listeners
        registerListeners();

        this.getLogger().info("CookieDough enabled!");

        // Tick games every second
        Bukkit.getScheduler().runTaskTimer(this, GameManager::tickGames, 0, 20);

        registerCommands();

        // Initialize LocaleManager and MessageScheduler
        getLocaleManager(); // Initialize LocaleManager
        messageScheduler = new MessageScheduler(this, getLocaleManager());
        messageScheduler.start();
        this.getLogger().info("MessageScheduler initialized and started");

        NPCReloadListener npcReloadListener = new NPCReloadListener(this);
        getServer().getPluginManager().registerEvents(npcReloadListener, this);
        GameNPC.setReloadListener(npcReloadListener);
    }

    public void registerCommands() {
        this.getCommand("lobby").setExecutor(new LobbyCommand(lobbyManager));
        this.getCommand("hub").setExecutor(new LobbyCommand(lobbyManager));
        this.getCommand("messagetest").setExecutor(new MessageTestCommand());
    }

    @Override
    public void onDisable() {
        // Stop MessageScheduler
        if (messageScheduler != null) {
            messageScheduler.stop();
        }

        // Plugin shutdown logic
        if (playerStatsService != null && playerStatsService.getEntityManager().isOpen()) {
            playerStatsService.getEntityManager().close();
        }
        if (sessionFactory != null) {
            sessionFactory.close();
        }

        // Make sure to prevent players from connecting to the server
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.kickPlayer("Server is shutting down. Please try again later.");
        });
        this.getLogger().info("CookieDough disabled!");
        Bukkit.setMaxPlayers(0); // Prevent new players from joining
    }
}

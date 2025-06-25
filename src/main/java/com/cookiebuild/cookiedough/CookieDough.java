package com.cookiebuild.cookiedough;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.commands.LobbyCommand;
import com.cookiebuild.cookiedough.commands.MessageTestCommand;
import com.cookiebuild.cookiedough.commands.SessionDiagnosticCommand;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.listener.NPCReloadListener;
import com.cookiebuild.cookiedough.listener.PlayerChatListener;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.WorldEventListener;
import com.cookiebuild.cookiedough.lobby.GameNPC;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.scheduler.MessageScheduler;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.cookiedough.service.PlayerStatsService;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.LocaleManager;
import com.cookiebuild.cookiedough.utils.RabbitMQInitializer;

public final class CookieDough extends JavaPlugin {
    private static CookieDough instance;
    private LobbyManager lobbyManager;
    private LocaleManager localeManager;
    private MessageScheduler messageScheduler;

    public static CookieDough getInstance() {
        return instance;
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new BaseEventBlocker(), this);
        getServer().getPluginManager().registerEvents(new PlayerWrapperListener(), this);
        getServer().getPluginManager().registerEvents(new WorldEventListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(new ChatManager()), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public static PlayerStatsService getPlayerStatsService() {
        return new PlayerStatsService(HibernateUtil.createEntityManager());
    }

    public static MinigameProgressionService createMinigameProgressionService() {
        return new MinigameProgressionService(HibernateUtil.createEntityManager());
    }

    public LocaleManager getLocaleManager() {
        if (localeManager == null) {
            localeManager = new LocaleManager();
        }
        return localeManager;
    }

    public MessageScheduler getMessageScheduler() {
        return messageScheduler;
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling CookieDough");
        instance = this;

        // Initialize utilities
        HibernateUtil.initialize();
        RabbitMQInitializer.initialize();
        getLocaleManager();

        // Initialize managers
        lobbyManager = new LobbyManager(this);
        messageScheduler = new MessageScheduler(this, getLocaleManager());
        messageScheduler.start();

        // Setup Lobby NPCs
        setupLobbyNPCs();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Start scheduled tasks
        Bukkit.getScheduler().runTaskTimer(this, GameManager::tickGames, 0, 20);
        getLogger().info("MessageScheduler initialized and started");

        // Special Listeners
        NPCReloadListener npcReloadListener = new NPCReloadListener(this);
        getServer().getPluginManager().registerEvents(npcReloadListener, this);
        GameNPC.setReloadListener(npcReloadListener);

        getLogger().info("CookieDough enabled!");
    }

    private void setupLobbyNPCs() {
        World lobbyWorld = getServer().getWorld("lobby");
        if (lobbyWorld == null) {
            getLogger().severe("Lobby world not found! NPCs cannot be created.");
            return;
        }

        Location microBattlesNpcLocation = new Location(lobbyWorld, 0.5, 8, 12.5, 180, 0);
        lobbyManager.addGameNpc("MicroBattles", microBattlesNpcLocation);

        Location pitchoutNpcLocation = new Location(lobbyWorld, 0.5, 8, -11.5, 0, 0);
        lobbyManager.addGameNpc("Pitchout", pitchoutNpcLocation);
    }

    public void registerCommands() {
        getCommand("lobby").setExecutor(new LobbyCommand(lobbyManager));
        getCommand("hub").setExecutor(new LobbyCommand(lobbyManager));
        getCommand("messagetest").setExecutor(new MessageTestCommand());
        getCommand("sessiondiag").setExecutor(new SessionDiagnosticCommand());
    }

    @Override
    public void onDisable() {
        if (messageScheduler != null) {
            messageScheduler.stop();
        }

        Bukkit.getOnlinePlayers()
                .forEach(player -> player.kickPlayer("Server is shutting down. Please try again later."));

        HibernateUtil.shutdown();

        getLogger().info("CookieDough disabled!");
    }
}

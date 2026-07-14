package com.cookiebuild.cookiedough;

import java.time.Duration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.commands.LobbyCommand;
import com.cookiebuild.cookiedough.commands.MessageTestCommand;
import com.cookiebuild.cookiedough.commands.SessionDiagnosticCommand;
import com.cookiebuild.cookiedough.commands.QuickPlayCommand;
import com.cookiebuild.cookiedough.commands.SocialSafetyCommand;
import com.cookiebuild.cookiedough.commands.PartyCommand;
import com.cookiebuild.cookiedough.commands.PracticeCommand;
import com.cookiebuild.cookiedough.commands.EventsCommand;
import com.cookiebuild.cookiedough.commands.GoalsCommand;
import com.cookiebuild.cookiedough.commands.FeedbackCommand;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.listener.NPCReloadListener;
import com.cookiebuild.cookiedough.listener.PlayerChatListener;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.WorldEventListener;
import com.cookiebuild.cookiedough.lobby.GameNPC;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.scheduler.MessageScheduler;
import com.cookiebuild.cookiedough.retention.PartyManager;
import com.cookiebuild.cookiedough.retention.PracticeManager;
import com.cookiebuild.cookiedough.retention.CommunityEventManager;
import com.cookiebuild.cookiedough.retention.PlayerGoalTracker;
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
    private ChatManager chatManager;
    private PlayerWrapperListener playerWrapperListener;
    private PartyManager partyManager;
    private PracticeManager practiceManager;
    private CommunityEventManager communityEventManager;
    private PlayerGoalTracker goalTracker;

    public static CookieDough getInstance() {
        return instance;
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new BaseEventBlocker(), this);
        playerWrapperListener = new PlayerWrapperListener();
        getServer().getPluginManager().registerEvents(playerWrapperListener, this);
        getServer().getPluginManager().registerEvents(new WorldEventListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(chatManager), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
        getServer().getPluginManager().registerEvents(practiceManager, this);
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public static PlayerStatsService getPlayerStatsService() {
        return new PlayerStatsService(null);
    }

    public static MinigameProgressionService createMinigameProgressionService() {
        return new MinigameProgressionService(null);
    }

    public LocaleManager getLocaleManager() {
        if (localeManager == null) {
            localeManager = new LocaleManager();
        }
        return localeManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public PracticeManager getPracticeManager() {
        return practiceManager;
    }

    public PlayerGoalTracker getGoalTracker() {
        return goalTracker;
    }

    public MessageScheduler getMessageScheduler() {
        return messageScheduler;
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling CookieDough");
        instance = this;
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        // Initialize utilities
        HibernateUtil.initialize();
        RabbitMQInitializer.initialize();
        getLocaleManager();
        chatManager = new ChatManager();
        partyManager = new PartyManager();
        goalTracker = new PlayerGoalTracker(this);
        practiceManager = new PracticeManager(this);
        communityEventManager = new CommunityEventManager(this);

        // Initialize managers
        lobbyManager = new LobbyManager(this);
        messageScheduler = new MessageScheduler(this, getLocaleManager());
        messageScheduler.start();

        NPCReloadListener npcReloadListener = new NPCReloadListener();
        getServer().getPluginManager().registerEvents(npcReloadListener, this);
        GameNPC.setReloadListener(npcReloadListener);

        // Setup Lobby NPCs
        setupLobbyNPCs();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Start scheduled tasks
        Bukkit.getScheduler().runTaskTimer(this, GameManager::tickGames, 0, 20);
        getLogger().info("MessageScheduler initialized and started");

        getLogger().info("CookieDough enabled!");
    }

    private void setupLobbyNPCs() {
        World lobbyWorld = getServer().getWorld("lobby");
        if (lobbyWorld == null) {
            getLogger().severe("Lobby world not found! NPCs cannot be created.");
            return;
        }

        // Initialize lobby signs (restore from main branch and add more)
        try {
            // First sign at original location (0, 8, 12) - will show first game (Pitchout)
            org.bukkit.block.BlockState firstState = lobbyWorld.getBlockAt(0, 8, 12).getState();
            Sign gameSign1 = firstState instanceof Sign sign ? sign : null;
            if (gameSign1 != null) {
                lobbyManager.addGameSign(gameSign1);
                getLogger().info("Added game sign #1 at (0, 8, 12): " + gameSign1);
            } else {
                getLogger().warning("No sign found at (0, 8, 12) in lobby world");
            }

            // Second sign above the first one (0, 9, 12) - will show second game
            // (MicroBattles)
            org.bukkit.block.BlockState secondState = lobbyWorld.getBlockAt(0, 9, 12).getState();
            Sign gameSign2 = secondState instanceof Sign sign ? sign : null;
            if (gameSign2 != null) {
                lobbyManager.addGameSign(gameSign2);
                getLogger().info("Added game sign #2 at (0, 9, 12): " + gameSign2);
            } else {
                getLogger().warning("No sign found at (0, 9, 12) in lobby world");
            }

            // Log total signs registered
            int registeredSigns = (gameSign1 != null ? 1 : 0) + (gameSign2 != null ? 1 : 0);
            getLogger().info("Total game signs registered: " + registeredSigns);

        } catch (Exception e) {
            getLogger().warning("Failed to add hardcoded signs: " + e.getMessage());
        }

        ConfigurationSection selectors = getConfig().getConfigurationSection("lobby.game-selectors");
        if (selectors == null) {
            getLogger().severe("No lobby.game-selectors are configured; game NPCs cannot be created.");
            return;
        }

        for (String selectorKey : selectors.getKeys(false)) {
            ConfigurationSection selector = selectors.getConfigurationSection(selectorKey);
            if (selector == null || !selector.getBoolean("enabled", true)) {
                continue;
            }
            String gameName = selector.getString("game");
            if (gameName == null || gameName.isBlank()) {
                // A ConfigurationSection created from an older persisted YAML does
                // not inherit newly copied nested defaults. Resolve the embedded
                // default explicitly before falling back to the selector key.
                org.bukkit.configuration.Configuration defaults = getConfig().getDefaults();
                gameName = defaults == null ? selectorKey : defaults.getString(
                        "lobby.game-selectors." + selectorKey + ".game", selectorKey);
            }
            World world = getServer().getWorld(selector.getString("world", "lobby"));
            if (world == null) {
                getLogger().warning("Skipping " + gameName + " selector: configured world is not loaded");
                continue;
            }
            Location location = new Location(world,
                    selector.getDouble("x"), selector.getDouble("y"), selector.getDouble("z"),
                    (float) selector.getDouble("yaw"), (float) selector.getDouble("pitch"));
            java.util.List<Double> offset = selector.getDoubleList("statue-offset");
            Vector statueOffset = offset.size() == 3
                    ? new Vector(offset.get(0), offset.get(1), offset.get(2))
                    : new Vector(2, 0, 0);
            lobbyManager.addGameNpc(gameName, location, statueOffset);
        }
    }

    public void registerCommands() {
        getCommand("lobby").setExecutor(new LobbyCommand(lobbyManager));
        getCommand("hub").setExecutor(new LobbyCommand(lobbyManager));
        getCommand("messagetest").setExecutor(new MessageTestCommand());
        getCommand("sessiondiag").setExecutor(new SessionDiagnosticCommand());
        getCommand("quickplay").setExecutor(new QuickPlayCommand(lobbyManager));
        SocialSafetyCommand socialSafety = new SocialSafetyCommand(chatManager);
        getCommand("mute").setExecutor(socialSafety);
        getCommand("block").setExecutor(socialSafety);
        getCommand("report").setExecutor(socialSafety);
        getCommand("party").setExecutor(new PartyCommand(partyManager));
        getCommand("practice").setExecutor(new PracticeCommand(practiceManager));
        getCommand("events").setExecutor(new EventsCommand(communityEventManager));
        getCommand("goals").setExecutor(new GoalsCommand(goalTracker));
        getCommand("feedback").setExecutor(new FeedbackCommand());
    }

    @Override
    public void onDisable() {
        if (messageScheduler != null) {
            messageScheduler.stop();
        }
        if (goalTracker != null) {
            goalTracker.shutdown();
        }

        PlayerWrapperListener.shutdownGracefully(Duration.ofSeconds(5));

        Bukkit.getOnlinePlayers().forEach(player ->
                player.kickPlayer("Server is shutting down. Please try again later."));

        HibernateUtil.shutdown();

        getLogger().info("CookieDough disabled!");
    }
}

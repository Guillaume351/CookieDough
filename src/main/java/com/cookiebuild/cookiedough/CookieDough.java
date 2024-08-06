package com.cookiebuild.cookiedough;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.commands.LobbyCommand;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.listener.BaseEventBlocker;
import com.cookiebuild.cookiedough.listener.PlayerChatListener;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.WorldEventListener;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.RabbitMQInitializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;
import org.hibernate.SessionFactory;

import java.util.ArrayList;
import java.util.List;

public final class CookieDough extends JavaPlugin {
    static CookieDough instance;
    public static SessionFactory sessionFactory;
    private static LobbyManager lobbyManager;

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

    @Override
    public void onEnable() {
        this.getLogger().info("Enabling CookieDough");
        // Affect instance
        instance = this;

        // Plugin startup logic
        sessionFactory = HibernateUtil.buildSessionFactory();

        // Initialize RabbitMQ
        RabbitMQInitializer.initialize();

        // Initialize lobby manager
        List<Sign> gameSigns = new ArrayList<>();
        gameSigns.add(getServer().getWorld("lobby").getBlockAt(0, 8, 12).getState() instanceof Sign ? (Sign) getServer().getWorld("lobby").getBlockAt(0, 8, 12).getState() : null);
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
    }

    public void registerCommands() {
        this.getCommand("lobby").setExecutor(new LobbyCommand(lobbyManager));
        this.getCommand("hub").setExecutor(new LobbyCommand(lobbyManager));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

}

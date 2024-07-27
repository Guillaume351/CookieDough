package com.cookiebuild.cookiedough;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.listener.PlayerChatListener;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.WorldEventListener;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.RabbitMQInitializer;
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
        getServer().getPluginManager().registerEvents(new PlayerWrapperListener(), this);
        getServer().getPluginManager().registerEvents(new WorldEventListener(), this);

        ChatManager chatManager = new ChatManager();
        getServer().getPluginManager().registerEvents(new PlayerChatListener(chatManager), this);
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

        // register listeners
        registerListeners();


        // Initialize lobby manager
        List<Sign> gameSigns = new ArrayList<>();
        gameSigns.add(getServer().getWorld("lobby").getBlockAt(0, 8, 12).getState() instanceof Sign ? (Sign) getServer().getWorld("lobby").getBlockAt(0, 8, 12).getState() : null);
        this.getLogger().info("Game signs: " + gameSigns);
        lobbyManager = new LobbyManager(this, gameSigns);

        this.getLogger().info("CookieDough enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

}

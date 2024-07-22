package com.cookiebuild.cookiedough;

import com.cookiebuild.cookiedough.chat.ChatManager;
import com.cookiebuild.cookiedough.listener.PlayerChatListener;
import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.listener.WorldEventListener;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import com.cookiebuild.cookiedough.utils.RabbitMQInitializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.hibernate.SessionFactory;

public final class CookieDough extends JavaPlugin {
    static CookieDough instance;
    public static SessionFactory sessionFactory;

    public static CookieDough getInstance() {
        return instance;
    }

    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerWrapperListener(), this);
        getServer().getPluginManager().registerEvents(new WorldEventListener(), this);

        ChatManager chatManager = new ChatManager();
        getServer().getPluginManager().registerEvents(new PlayerChatListener(chatManager), this);
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

package com.cookiebuild.cookiedough;

import com.cookiebuild.cookiedough.listener.PlayerWrapperListener;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.utils.HibernateUtil;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.hibernate.SessionFactory;

import java.util.ArrayList;

public final class CookieDough extends JavaPlugin {
    static CookieDough instance;
    public static SessionFactory sessionFactory;

    public static CookieDough getInstance() {
        return instance;
    }


    public void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerWrapperListener(), this);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        sessionFactory = HibernateUtil.buildSessionFactory();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}

package com.cookiebuild.cookiedough.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import com.cookiebuild.cookiedough.model.ChatMessage;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.model.PlayerSession;

public class HibernateUtil {
    private static final List<Class<?>> additionalEntities = new ArrayList<>();

    /**
     * Register an entity class to be included in the Hibernate configuration.
     * This method should be called by other modules before the SessionFactory is
     * built.
     * 
     * @param entityClass The entity class to register
     */
    public static void registerEntity(Class<?> entityClass) {
        additionalEntities.add(entityClass);
    }

    public static SessionFactory buildSessionFactory() {
        Configuration configuration = new Configuration();

        // Load properties from environment variables
        Properties properties = new Properties();
        properties.put(Environment.DRIVER, System.getenv("HIBERNATE_CONNECTION_DRIVER_CLASS"));
        properties.put(Environment.URL, System.getenv("HIBERNATE_CONNECTION_URL"));
        properties.put(Environment.USER, System.getenv("HIBERNATE_CONNECTION_USERNAME"));
        properties.put(Environment.PASS, System.getenv("HIBERNATE_CONNECTION_PASSWORD"));
        properties.put(Environment.DIALECT, System.getenv("HIBERNATE_DIALECT"));
        properties.put(Environment.SHOW_SQL, System.getenv("HIBERNATE_SHOW_SQL"));
        properties.put(Environment.HBM2DDL_AUTO, System.getenv("HIBERNATE_HBM2DDL_AUTO"));

        properties.put("hibernate.c3p0.min_size", System.getenv("HIBERNATE_C3P0_MIN_SIZE"));
        properties.put("hibernate.c3p0.max_size", System.getenv("HIBERNATE_C3P0_MAX_SIZE"));

        configuration.setProperties(properties);

        // Register core CookieDough entities
        configuration.addAnnotatedClass(PlayerData.class);
        configuration.addAnnotatedClass(ChatMessage.class);
        configuration.addAnnotatedClass(Match.class);
        configuration.addAnnotatedClass(PlayerMatchPerformance.class);
        configuration.addAnnotatedClass(PlayerSession.class); // Add PlayerSession entity

        // Register additional entities from other modules
        for (Class<?> entityClass : additionalEntities) {
            configuration.addAnnotatedClass(entityClass);
        }

        return configuration.buildSessionFactory();
    }
}

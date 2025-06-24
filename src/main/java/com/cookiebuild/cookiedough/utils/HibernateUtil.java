package com.cookiebuild.cookiedough.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import com.cookiebuild.cookiedough.model.ChatMessage;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.MinigameStats;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.model.PlayerSession;

import jakarta.persistence.EntityManager;

public class HibernateUtil {
    private static final List<Class<?>> additionalEntities = new ArrayList<>();
    private static SessionFactory sessionFactory;

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

    public static void initialize() {
        if (sessionFactory != null) {
            return; // Already initialized
        }

        try {
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

            configuration.setProperties(properties);

            // Register core CookieDough entities
            configuration.addAnnotatedClass(PlayerData.class);
            configuration.addAnnotatedClass(ChatMessage.class);
            configuration.addAnnotatedClass(Match.class);
            configuration.addAnnotatedClass(PlayerMatchPerformance.class);
            configuration.addAnnotatedClass(PlayerSession.class); // Add PlayerSession entity
            configuration.addAnnotatedClass(MinigameStats.class); // Add MinigameStats entity

            // Register additional entities from other modules
            for (Class<?> entityClass : additionalEntities) {
                configuration.addAnnotatedClass(entityClass);
            }

            sessionFactory = configuration.buildSessionFactory();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ExceptionInInitializerError(e);
        }
    }

    public static EntityManager createEntityManager() {
        if (sessionFactory == null) {
            throw new IllegalStateException(
                    "Hibernate has not been initialized. Call HibernateUtil.initialize() first.");
        }
        return sessionFactory.createEntityManager();
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}

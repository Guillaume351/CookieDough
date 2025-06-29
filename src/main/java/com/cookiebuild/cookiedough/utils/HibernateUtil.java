package com.cookiebuild.cookiedough.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import com.cookiebuild.cookiedough.model.ChatMessage;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.MinigameProgression;
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

            // Add HikariCP connection pooling configuration
            properties.put("hibernate.connection.provider_class",
                    "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");

            // Connection pool size settings
            properties.put("hibernate.hikari.maximumPoolSize", "30");
            properties.put("hibernate.hikari.minimumIdle", "5");

            // Connection timeout settings (in milliseconds)
            properties.put("hibernate.hikari.connectionTimeout", "30000"); // 30 seconds
            properties.put("hibernate.hikari.idleTimeout", "300000"); // 5 minutes
            properties.put("hibernate.hikari.maxLifetime", "900000"); // 15 minutes

            // Connection validation
            properties.put("hibernate.hikari.connectionTestQuery", "SELECT 1");

            // Leak detection (useful for debugging)
            properties.put("hibernate.hikari.leakDetectionThreshold", "60000"); // 1 minute

            // Pool name for monitoring
            properties.put("hibernate.hikari.poolName", "CookieDoughHikariCP");

            configuration.setProperties(properties);

            // Register core CookieDough entities
            configuration.addAnnotatedClass(PlayerData.class);
            configuration.addAnnotatedClass(ChatMessage.class);
            configuration.addAnnotatedClass(Match.class);
            configuration.addAnnotatedClass(PlayerMatchPerformance.class);
            configuration.addAnnotatedClass(PlayerSession.class); // Add PlayerSession entity
            configuration.addAnnotatedClass(MinigameProgression.class); // Add MinigameProgression entity

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
        EntityManager entityManager = sessionFactory.createEntityManager();
        return entityManager;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}

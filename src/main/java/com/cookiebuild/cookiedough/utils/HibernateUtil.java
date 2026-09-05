package com.cookiebuild.cookiedough.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.JdbcSettings;

import com.cookiebuild.cookiedough.model.ChatMessage;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.model.PlayerData;
import com.cookiebuild.cookiedough.model.PlayerMatchPerformance;
import com.cookiebuild.cookiedough.model.PlayerSession;
import com.cookiebuild.cookiedough.model.CoinTransaction;
import com.cookiebuild.cookiedough.model.PlayerLinkChallenge;
import com.cookiebuild.cookiedough.model.CosmeticEntitlement;
import com.cookiebuild.cookiedough.model.CosmeticSelection;

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
            properties.put(JdbcSettings.JAKARTA_JDBC_DRIVER,
                    requireEnvironmentVariable("HIBERNATE_CONNECTION_DRIVER_CLASS"));
            properties.put(JdbcSettings.JAKARTA_JDBC_URL,
                    requireEnvironmentVariable("HIBERNATE_CONNECTION_URL"));
            properties.put(JdbcSettings.JAKARTA_JDBC_USER,
                    requireEnvironmentVariable("HIBERNATE_CONNECTION_USERNAME"));
            properties.put(JdbcSettings.JAKARTA_JDBC_PASSWORD,
                    requireEnvironmentVariable("HIBERNATE_CONNECTION_PASSWORD"));
            putEnvironmentVariableIfPresent(properties, Environment.DIALECT, "HIBERNATE_DIALECT");
            properties.put(Environment.SHOW_SQL, environmentVariableOrDefault("HIBERNATE_SHOW_SQL", "false"));
            properties.put(Environment.HBM2DDL_AUTO,
                    environmentVariableOrDefault("HIBERNATE_HBM2DDL_AUTO", "validate"));

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
            configuration.addAnnotatedClass(CoinTransaction.class);
            configuration.addAnnotatedClass(PlayerLinkChallenge.class);
            configuration.addAnnotatedClass(CosmeticEntitlement.class);
            configuration.addAnnotatedClass(CosmeticSelection.class);

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
            sessionFactory = null;
        }
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

    private static String environmentVariableOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static void putEnvironmentVariableIfPresent(Properties properties, String propertyName,
            String environmentVariableName) {
        String value = System.getenv(environmentVariableName);
        if (value != null && !value.isBlank()) {
            properties.put(propertyName, value);
        }
    }
}

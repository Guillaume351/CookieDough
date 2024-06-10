package com.cookiebuild.cookiedough.utils;

import com.cookiebuild.cookiedough.model.PlayerData;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;

import java.util.Properties;

public class HibernateUtil {

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
        configuration.addAnnotatedClass(PlayerData.class);

        return configuration.buildSessionFactory();
    }
}

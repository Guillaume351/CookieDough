package com.cookiebuild.cookiedough.utils;

/** Creates or updates the core database schema without starting a Paper server. */
public final class HibernateSchemaBootstrap {
    private HibernateSchemaBootstrap() {
    }

    public static void main(String[] args) {
        HibernateUtil.initialize();
        HibernateUtil.shutdown();
    }
}

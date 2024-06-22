package com.cookiebuild.cookiedough.utils;

import com.cookiebuild.cookiedough.rabbitmq.RabbitMQConnection;

public class RabbitMQInitializer {
    public static void initialize() {
        try {
            RabbitMQConnection.initialize(System.getenv("RABBITMQ_HOST"), Integer.parseInt(System.getenv("RABBITMQ_PORT")));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

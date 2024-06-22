package com.cookiebuild.cookiedough.rabbitmq;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQConnection {

    private static Connection connection;

    public static void initialize(String host, int port) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        connection = factory.newConnection();
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void close() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
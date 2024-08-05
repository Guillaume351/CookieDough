package com.cookiebuild.cookiedough.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class RabbitMQPublisher {

    private final String exchangeName;

    public RabbitMQPublisher(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public void publish(String routingKey, String message) throws Exception {
        try (Connection connection = RabbitMQConnection.getConnection()) {
            try (Channel channel = connection.createChannel()) {
                channel.exchangeDeclare(exchangeName, "topic", true);
                channel.basicPublish(exchangeName, routingKey, null, message.getBytes("UTF-8"));
            }
        }
    }
}
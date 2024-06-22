package com.cookiebuild.cookiedough.rabbitmq;

import com.rabbitmq.client.*;

public class RabbitMQSubscriber {

    private final String exchangeName;
    private final String queueName;
    private final String routingKey;

    public RabbitMQSubscriber(String exchangeName, String queueName, String routingKey) {
        this.exchangeName = exchangeName;
        this.queueName = queueName;
        this.routingKey = routingKey;
    }

    public void subscribe(DeliverCallback deliverCallback) throws Exception {
        try (Connection connection = RabbitMQConnection.getConnection()) {
            try (Channel channel = connection.createChannel()) {
                channel.exchangeDeclare(exchangeName, "topic", true);
                channel.queueDeclare(queueName, true, false, false, null);
                channel.queueBind(queueName, exchangeName, routingKey);

                channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
            }
        }
    }
}
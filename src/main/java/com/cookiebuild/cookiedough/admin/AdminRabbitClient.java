package com.cookiebuild.cookiedough.admin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.Logger;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;

/** One persistent RabbitMQ connection with explicit reconnect and manual ACKs. */
final class AdminRabbitClient implements AutoCloseable {
    private final AdminBridgeConfig config;
    private final Logger logger;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CookieDough-admin-rabbit");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean stopping = new AtomicBoolean();
    private volatile Connection connection;
    private volatile Channel consumerChannel;
    private volatile Channel publisherChannel;
    private volatile Function<String, CompletionStage<Void>> handler;

    AdminRabbitClient(AdminBridgeConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    void start(Function<String, CompletionStage<Void>> handler) {
        this.handler = handler;
        io.execute(this::connectIfNeeded);
        io.scheduleWithFixedDelay(this::connectIfNeeded,
                config.reconnectDelayMillis(), config.reconnectDelayMillis(), TimeUnit.MILLISECONDS);
    }

    CompletableFuture<Void> publish(String routingKey, String json) {
        CompletableFuture<Void> published = new CompletableFuture<>();
        io.execute(() -> {
            Channel channel = publisherChannel;
            if (stopping.get() || channel == null || !channel.isOpen()) {
                published.completeExceptionally(new IllegalStateException("Admin RabbitMQ publisher is disconnected"));
                return;
            }
            try {
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .contentEncoding(StandardCharsets.UTF_8.name())
                        .deliveryMode(2)
                        .timestamp(new Date())
                        .build();
                channel.basicPublish(config.exchange(), routingKey, true, properties,
                        json.getBytes(StandardCharsets.UTF_8));
                channel.waitForConfirmsOrDie(Duration.ofSeconds(5).toMillis());
                published.complete(null);
            } catch (Exception error) {
                invalidateConnection();
                published.completeExceptionally(error);
            }
        });
        return published;
    }

    boolean connected() {
        return healthy();
    }

    private boolean healthy() {
        Connection current = connection;
        Channel consumer = consumerChannel;
        Channel publisher = publisherChannel;
        return current != null && current.isOpen()
                && consumer != null && consumer.isOpen()
                && publisher != null && publisher.isOpen();
    }

    private void connectIfNeeded() {
        if (stopping.get() || healthy()) return;
        invalidateConnection();
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setAutomaticRecoveryEnabled(false);
            factory.setRequestedHeartbeat(30);
            factory.setConnectionTimeout(10_000);
            factory.setHandshakeTimeout(10_000);
            if (config.rabbitUrl() != null) {
                factory.setUri(URI.create(config.rabbitUrl()));
            } else {
                factory.setHost(config.rabbitHost());
                factory.setPort(config.rabbitPort());
                factory.setUsername(config.rabbitUsername());
                factory.setPassword(config.rabbitPassword());
                factory.setVirtualHost(config.rabbitVirtualHost());
            }
            Connection newConnection = factory.newConnection("CookieDough-admin-" + config.serverId());
            Channel newConsumer = newConnection.createChannel();
            Channel newPublisher = newConnection.createChannel();
            declareTopology(newConsumer);
            newPublisher.exchangeDeclare(config.exchange(), "topic", true);
            newPublisher.confirmSelect();
            newConsumer.basicQos(1);
            newConsumer.basicConsume(config.commandQueue(), false,
                    (consumerTag, delivery) -> onDelivery(newConsumer, consumerTag, delivery), ignored -> { });
            connection = newConnection;
            consumerChannel = newConsumer;
            publisherChannel = newPublisher;
            logger.info("AdminBridge connected to RabbitMQ as " + config.serverId());
        } catch (Exception error) {
            invalidateConnection();
            logger.warning("AdminBridge RabbitMQ connection failed; retrying without affecting gameplay: "
                    + safeMessage(error));
        }
    }

    private void declareTopology(Channel channel) throws Exception {
        channel.exchangeDeclare(config.exchange(), "topic", true);
        channel.queueDeclare(config.deadLetterQueue(), true, false, false, null);
        channel.queueBind(config.deadLetterQueue(), config.exchange(), config.deadLetterRoutingKey());
        channel.queueDeclare(config.commandQueue(), true, false, false,
                Map.of(
                        "x-message-ttl", config.commandTtlMillis(),
                        "x-dead-letter-exchange", config.exchange(),
                        "x-dead-letter-routing-key", config.deadLetterRoutingKey()));
        channel.queueBind(config.commandQueue(), config.exchange(), config.serverCommandRoutingKey());
        channel.queueBind(config.commandQueue(), config.exchange(), "commands.all");
    }

    private void onDelivery(Channel deliveryChannel, String consumerTag, Delivery delivery) {
        String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
        CompletionStage<Void> handled;
        try {
            handled = handler.apply(body);
        } catch (RuntimeException error) {
            handled = CompletableFuture.failedFuture(error);
        }
        handled.whenComplete((ignored, error) -> io.execute(
                () -> acknowledge(deliveryChannel, delivery.getEnvelope().getDeliveryTag(), error)));
    }

    private void acknowledge(Channel channel, long deliveryTag, Throwable error) {
        if (channel != consumerChannel || channel == null || !channel.isOpen()) return;
        try {
            if (error == null) {
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (Exception ackError) {
            invalidateConnection();
        }
    }

    private void invalidateConnection() {
        closeQuietly(consumerChannel);
        closeQuietly(publisherChannel);
        closeQuietly(connection);
        consumerChannel = null;
        publisherChannel = null;
        connection = null;
    }

    private static void closeQuietly(Channel channel) {
        if (channel == null) return;
        try {
            if (channel.isOpen()) channel.close();
        } catch (Exception ignored) { }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            if (connection.isOpen()) connection.close();
        } catch (Exception ignored) { }
    }

    private static String safeMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        if (message == null || message.isBlank()) return root.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]", " ");
        message = message.replaceAll("amqps?://[^\\s/@]+(?::[^\\s/@]*)?@", "amqp://***@");
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

    @Override
    public void close() {
        if (!stopping.compareAndSet(false, true)) return;
        io.execute(this::invalidateConnection);
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }
    }
}

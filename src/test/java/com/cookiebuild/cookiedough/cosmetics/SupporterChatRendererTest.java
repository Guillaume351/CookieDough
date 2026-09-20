package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class SupporterChatRendererTest {
    @Test
    void wrapperChangesOnlyAuthorizedDisplayNameAndPreservesMessageAndViewer() {
        UUID playerId = UUID.randomUUID();
        Player source = player(playerId);
        Component message = Component.text("hello");
        Audience viewer = Audience.empty();
        AtomicReference<Component> delegatedName = new AtomicReference<>();
        AtomicReference<Component> delegatedMessage = new AtomicReference<>();
        AtomicReference<Audience> delegatedViewer = new AtomicReference<>();
        ChatRenderer delegate = (ignoredSource, displayName, renderedMessage, renderedViewer) -> {
            delegatedName.set(displayName);
            delegatedMessage.set(renderedMessage);
            delegatedViewer.set(renderedViewer);
            return renderedMessage;
        };

        Component rendered = SupporterChatRenderer.wrap(delegate, playerId::equals)
                .render(source, Component.text("Alex"), message, viewer);

        assertSame(message, rendered);
        assertSame(message, delegatedMessage.get());
        assertSame(viewer, delegatedViewer.get());
        assertEquals("[Supporter] Alex", PlainTextComponentSerializer.plainText()
                .serialize(delegatedName.get()));
    }

    @Test
    void wrapperLeavesUnauthorizedDisplayNameUnmodified() {
        UUID playerId = UUID.randomUUID();
        Player source = player(playerId);
        Component displayName = Component.text("Alex");
        AtomicReference<Component> delegatedName = new AtomicReference<>();
        ChatRenderer delegate = (ignoredSource, name, message, viewer) -> {
            delegatedName.set(name);
            return message;
        };

        SupporterChatRenderer.wrap(delegate, ignored -> false)
                .render(source, displayName, Component.text("hello"), Audience.empty());

        assertSame(displayName, delegatedName.get());
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                SupporterChatRendererTest.class.getClassLoader(),
                new Class<?>[] { Player.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "getName" -> "Alex";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        return 0d;
    }
}

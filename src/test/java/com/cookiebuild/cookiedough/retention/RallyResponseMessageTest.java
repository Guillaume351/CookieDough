package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class RallyResponseMessageTest {
    @Test
    void joiningResponseUsesOnlyFixedLocalizedCopy() {
        RallyRepository.Response response = response(
                "CookieFan", RallyRepository.ResponseKind.JOINING, "pitchout");

        Component message = RallyManager.responseMessage(response, Locale.FRENCH);

        assertEquals("CookieFan a répondu « J’arrive » pour Pitchout. Laisse-lui 2 à 3 minutes !",
                PlainTextComponentSerializer.plainText().serialize(message));
        assertFalse(message.children().stream().anyMatch(child -> child.clickEvent() != null));
    }

    @Test
    void unavailableResponseSanitizesTheHistoricNameBeforeTextAndFriendCommand() {
        RallyRepository.Response response = response(
                "Bad\nName;op @a", RallyRepository.ResponseKind.UNAVAILABLE, "network");

        Component message = RallyManager.responseMessage(response, Locale.ENGLISH);
        Component action = message.children().getLast();

        assertEquals("Bad_Name_op _a isn't available for Cookie Build. Add Bad_Name_op _a as a friend",
                PlainTextComponentSerializer.plainText().serialize(message));
        assertEquals(ClickEvent.Action.RUN_COMMAND, action.clickEvent().action());
        assertEquals("/friend add Bad_Name_op _a",
                ((ClickEvent.Payload.Text) action.clickEvent().payload()).value());
    }

    @Test
    void responseContractRejectsUnknownKindsAndGamemodes() {
        assertEquals(RallyRepository.ResponseKind.JOINING,
                RallyRepository.ResponseKind.fromWireValue("joining"));
        assertThrows(IllegalArgumentException.class,
                () -> RallyRepository.ResponseKind.fromWireValue("custom text"));
        assertThrows(IllegalArgumentException.class, () -> response(
                "CookieFan", RallyRepository.ResponseKind.JOINING, "unknown"));
    }

    private static RallyRepository.Response response(
            String responderName, RallyRepository.ResponseKind kind, String gamemode) {
        return new RallyRepository.Response(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                responderName,
                kind,
                gamemode);
    }
}

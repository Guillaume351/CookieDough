package com.cookiebuild.cookiedough.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatManagerTest {
    @Test
    void replacesAndUpdatesPersistedBlockState() {
        ChatManager chat = new ChatManager();
        UUID viewer = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        chat.replaceBlockedPlayers(viewer, Set.of(first));
        assertTrue(chat.isBlocked(viewer, first));
        assertFalse(chat.isBlocked(viewer, second));

        chat.setBlocked(viewer, first, false);
        chat.setBlocked(viewer, second, true);
        assertFalse(chat.isBlocked(viewer, first));
        assertTrue(chat.isBlocked(viewer, second));

        chat.cleanup(second);
        assertTrue(chat.isBlocked(viewer, second));
        chat.cleanup(viewer);
        assertFalse(chat.isBlocked(viewer, second));
    }
}

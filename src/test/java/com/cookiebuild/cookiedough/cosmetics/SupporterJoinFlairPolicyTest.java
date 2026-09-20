package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupporterJoinFlairPolicyTest {
    @Test
    void flairIsLocalLobbyOnlyAndAtMostOncePerConnection() {
        assertFalse(SupporterJoinFlairPolicy.shouldPlay(false, true, false));
        assertFalse(SupporterJoinFlairPolicy.shouldPlay(true, false, false));
        assertTrue(SupporterJoinFlairPolicy.shouldPlay(true, true, false));
        assertFalse(SupporterJoinFlairPolicy.shouldPlay(true, true, true));
    }

    @Test
    void aReconnectStartsANewSessionGuard() {
        boolean previousConnectionPlayed = true;
        assertFalse(SupporterJoinFlairPolicy.shouldPlay(true, true, previousConnectionPlayed));
        boolean newConnectionPlayed = false;
        assertTrue(SupporterJoinFlairPolicy.shouldPlay(true, true, newConnectionPlayed));
    }
}

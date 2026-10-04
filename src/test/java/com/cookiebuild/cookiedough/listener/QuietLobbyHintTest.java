package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A quiet lobby never tells a lone player that Build Battle waits for a second player. */
class QuietLobbyHintTest {
    @Test
    void aSoloStartFeaturedModeUsesTheSoloHint() {
        assertEquals("lobby.solo.featured_solo", PlayerWrapperListener.featuredHintKey(true));
        assertEquals("lobby.solo.featured", PlayerWrapperListener.featuredHintKey(false));
    }
}

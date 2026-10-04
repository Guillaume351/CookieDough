package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A lone Build Battle caller starts anyway: never tell him to wait minutes. */
class RallyCallLaunchedKeyTest {
    @Test
    void soloStartQueuesGetTheNoWaitConfirmation() {
        assertEquals("rally.call.game.launched_solo",
                RallyManager.callLaunchedKey(RallyRepository.Source.PLAYER, true));
        assertEquals("rally.call.game.launched_solo",
                RallyManager.callLaunchedKey(RallyRepository.Source.AUTOMATIC, true));
        assertEquals("rally.call.game.launched",
                RallyManager.callLaunchedKey(RallyRepository.Source.PLAYER, false));
        assertEquals("rally.call.login.launched",
                RallyManager.callLaunchedKey(RallyRepository.Source.LOGIN, true));
    }
}

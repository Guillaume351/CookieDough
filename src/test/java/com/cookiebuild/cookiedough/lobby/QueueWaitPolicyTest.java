package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.lobby.QueueWaitPolicy.Prompt;

class QueueWaitPolicyTest {
    @Test
    void offersOptionsToALonePlayerAtTwentySecondsOnlyOnce() {
        assertEquals(Prompt.NONE, QueueWaitPolicy.next(19, true, false, false, false));
        assertEquals(Prompt.OPTIONS, QueueWaitPolicy.next(20, true, false, false, false));
        assertEquals(Prompt.NONE, QueueWaitPolicy.next(30, true, true, false, false));
    }

    @Test
    void groupsWaitingForACompositionGetOnlyALightHint() {
        assertEquals(Prompt.HINT, QueueWaitPolicy.next(20, false, false, false, true));
        assertEquals(Prompt.NONE, QueueWaitPolicy.next(60, false, true, false, true));
    }

    @Test
    void proposesAConsentedSwitchAtFortyFiveSecondsOnlyWhenAnotherQueueHasPlayers() {
        assertEquals(Prompt.NONE, QueueWaitPolicy.next(44, true, true, false, true));
        assertEquals(Prompt.SWITCH_OFFER, QueueWaitPolicy.next(45, true, true, false, true));
        assertEquals(Prompt.NONE, QueueWaitPolicy.next(45, true, true, false, false));
        assertEquals(Prompt.NONE, QueueWaitPolicy.next(90, true, true, true, true));
    }

    @Test
    void scansOtherQueuesOnlyWhenAPromptCouldUseThem() {
        assertFalse(QueueWaitPolicy.needsSwitchCandidate(10, true, false, false));
        assertTrue(QueueWaitPolicy.needsSwitchCandidate(20, true, false, false));
        assertFalse(QueueWaitPolicy.needsSwitchCandidate(30, true, true, false));
        assertTrue(QueueWaitPolicy.needsSwitchCandidate(45, true, true, false));
        assertFalse(QueueWaitPolicy.needsSwitchCandidate(45, false, false, false));
    }
}

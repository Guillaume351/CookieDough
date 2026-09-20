package com.cookiebuild.cookiedough.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerJoinNotificationPolicyTest {
    @Test
    void onlyPlayersAlreadyOnlineReceiveTheArrivalSound() {
        UUID joining = UUID.randomUUID();
        UUID existing = UUID.randomUUID();

        assertTrue(PlayerWrapperListener.shouldPlayJoinNotification(joining, existing));
        assertFalse(PlayerWrapperListener.shouldPlayJoinNotification(joining, joining));
        assertFalse(PlayerWrapperListener.shouldPlayJoinNotification(null, existing));
    }
}

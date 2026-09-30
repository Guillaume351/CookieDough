package com.cookiebuild.cookiedough.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.social.SocialMenuAction.Kind;

class SocialMenuActionTest {
    @Test
    void roundTripsEveryKindThroughItsEncodedForm() {
        UUID leader = UUID.randomUUID();
        for (Kind kind : Kind.values()) {
            SocialMenuAction action = switch (kind) {
                case FRIEND, REQUEST, CONFIRM_REMOVE, ACCEPT, DENY, SEND_REQUEST, INVITE, JOIN_QUEUE, REMOVE ->
                        SocialMenuAction.of(kind, ".Bedrock_Player 1");
                case JOIN_PARTY -> SocialMenuAction.of(kind, leader.toString());
                default -> SocialMenuAction.of(kind);
            };
            assertEquals(action, SocialMenuAction.parse(action.encode()).orElseThrow(), kind.name());
        }
    }

    @Test
    void rejectsTamperedOrMalformedPayloads() {
        assertTrue(SocialMenuAction.parse(null).isEmpty());
        assertTrue(SocialMenuAction.parse("").isEmpty());
        assertTrue(SocialMenuAction.parse("unknown").isEmpty());
        assertTrue(SocialMenuAction.parse("home:extra").isEmpty());
        assertTrue(SocialMenuAction.parse("accept").isEmpty());
        assertTrue(SocialMenuAction.parse("accept:bad;name").isEmpty());
        assertTrue(SocialMenuAction.parse("join_party:not-a-uuid").isEmpty());
        assertTrue(SocialMenuAction.parse("remove:" + "x".repeat(40)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SocialMenuAction.of(Kind.INVITE));
    }

    @Test
    void navigationKindsOpenPagesAndMutationsDoNot() {
        assertTrue(SocialMenuAction.of(Kind.PARTY).navigation());
        assertEquals(SocialPageId.FRIEND, SocialMenuAction.of(Kind.FRIEND, "Alex").kind().page());
        assertFalse(SocialMenuAction.of(Kind.INVITE, "Alex").navigation());
        assertFalse(SocialMenuAction.of(Kind.CLOSE).navigation());
    }
}

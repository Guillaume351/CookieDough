package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RewardGrantTextTest {
    @Test
    void mapsWebsiteSourcesToLocalizedMessages() {
        assertEquals("rewards.app.link", RewardGrantText.messageKey("app_link"));
        assertEquals("rewards.app.daily", RewardGrantText.messageKey("app_daily"));
        assertEquals("rewards.generic", RewardGrantText.messageKey("referral"));
        assertEquals("rewards.generic", RewardGrantText.messageKey(null));
    }

    @Test
    void listsOnlyNonEmptyParts() {
        assertEquals("+150 pièces + Badge Appli",
                RewardGrantText.parts(150, 0, "Badge Appli", coins -> "+" + coins + " pièces"));
        assertEquals("+40 pièces", RewardGrantText.parts(40, 0, null, coins -> "+" + coins + " pièces"));
        assertEquals("+10 XP", RewardGrantText.parts(0, 10, " ", coins -> "unused"));
    }
}

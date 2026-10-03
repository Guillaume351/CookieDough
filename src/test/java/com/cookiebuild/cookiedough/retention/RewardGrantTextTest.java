package com.cookiebuild.cookiedough.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RewardGrantTextTest {
    @Test
    void mapsWebsiteSourcesToLocalizedMessages() {
        assertEquals("rewards.app.link", RewardGrantText.messageKey("app_link"));
        assertEquals("rewards.app.daily", RewardGrantText.messageKey("app_daily"));
        assertEquals("rewards.bb.bestof", RewardGrantText.messageKey("bb_bestof"));
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

    @Test
    void bestOfGrantReadsAsTheWeeklyGalleryNoticeInEveryLocale() {
        assertEquals("Ta construction est dans le best-of de la semaine : +150 pièces !",
                com.cookiebuild.cookiedough.utils.LocaleManager.getMessage(RewardGrantText.messageKey("bb_bestof"),
                        java.util.Locale.FRENCH, RewardGrantText.parts(150, 0, null, coins -> com.cookiebuild
                                .cookiedough.utils.LocaleManager.getMessage("goals.reward_coins",
                                        java.util.Locale.FRENCH, coins))));
        for (java.util.Locale locale : java.util.List.of(java.util.Locale.ENGLISH, java.util.Locale.GERMAN,
                java.util.Locale.ITALIAN, java.util.Locale.of("es"), java.util.Locale.of("pt", "BR"),
                java.util.Locale.of("bg"), java.util.Locale.of("hi"))) {
            String text = com.cookiebuild.cookiedough.utils.LocaleManager.getMessage("rewards.bb.bestof", locale, "+50");
            org.junit.jupiter.api.Assertions.assertTrue(text.contains("+50") && !text.equals("rewards.bb.bestof"),
                    locale + ": " + text);
        }
    }
}

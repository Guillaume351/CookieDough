package com.cookiebuild.cookiedough.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SocialSafetyCommandTest {
    @Test
    void mapsFreeFormDetailsToStructuredReportCategories() {
        assertEquals("spam", SocialSafetyCommand.reportCategory("chat spam"));
        assertEquals("hate_or_discrimination", SocialSafetyCommand.reportCategory("propos racistes"));
        assertEquals("sexual_content", SocialSafetyCommand.reportCategory("sexual content"));
        assertEquals("threats", SocialSafetyCommand.reportCategory("menace un joueur"));
        assertEquals("impersonation", SocialSafetyCommand.reportCategory("usurpation d'identité"));
        assertEquals("cheating", SocialSafetyCommand.reportCategory("possible hack"));
        assertEquals("inappropriate_name", SocialSafetyCommand.reportCategory("pseudo déplacé"));
        assertEquals("harassment", SocialSafetyCommand.reportCategory("comportement agressif"));
    }
}

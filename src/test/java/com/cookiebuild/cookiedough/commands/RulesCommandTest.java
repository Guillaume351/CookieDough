package com.cookiebuild.cookiedough.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class RulesCommandTest {
    @Test
    void pointsToTheCanonicalSecureRulesPage() {
        URI rules = URI.create(RulesCommand.RULES_URL);
        assertEquals("https", rules.getScheme());
        assertEquals("www.cookie-build.com", rules.getHost());
        assertEquals("/rules", rules.getPath());
    }
}

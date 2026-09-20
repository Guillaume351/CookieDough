package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

class SupporterTitleFormatterTest {
    @Test
    void supporterPrefixDecoratesOnlyTheDisplayNameComponent() {
        Component original = Component.text("Alex");
        Component decorated = SupporterTitleFormatter.decorate(original, true);
        assertEquals("[Supporter] Alex",
                PlainTextComponentSerializer.plainText().serialize(decorated));
        assertEquals("Alex", PlainTextComponentSerializer.plainText().serialize(original));
    }

    @Test
    void unauthorizedNameIsReturnedUnmodified() {
        Component original = Component.text("Alex");
        assertSame(original, SupporterTitleFormatter.decorate(original, false));
    }
}

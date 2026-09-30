package com.cookiebuild.cookiedough.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.Test;

class CosmeticBadgesTest {
    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void appBadgeUsesEmojiOnJavaAndTextOnBedrock() {
        Component name = Component.text("Alex");
        assertEquals("[📱] Alex", plain(CosmeticBadges.decorate(name, CosmeticCatalog.APP_COMPANION_BADGE, false)));
        assertEquals("[App] Alex", plain(CosmeticBadges.decorate(name, CosmeticCatalog.APP_COMPANION_BADGE, true)));
        assertEquals("[App] ", plain(CosmeticBadges.tabPrefix(CosmeticCatalog.APP_COMPANION_BADGE).orElseThrow()));
    }

    @Test
    void supporterBadgeKeepsItsExistingLabels() {
        Component name = Component.text("Alex");
        assertEquals("[Supporter] Alex", plain(CosmeticBadges.decorate(name, CosmeticCatalog.SUPPORTER_BADGE, true)));
        assertEquals("★ Supporter ", plain(CosmeticBadges.tabPrefix(CosmeticCatalog.SUPPORTER_BADGE).orElseThrow()));
        assertEquals("Alex", plain(CosmeticBadges.decorate(name, null, false)));
        assertTrue(CosmeticBadges.tabPrefix(CosmeticCatalog.NOTE_TRAIL).isEmpty());
    }

    @Test
    void everyTrailHasANativeParticle() {
        for (CosmeticDefinition item : CosmeticCatalog.items()) {
            if (item.slot() == CosmeticSlot.HUB_TRAIL) {
                assertTrue(CosmeticEffects.trailParticle(item.id()) != null, item.id());
            }
        }
    }
}

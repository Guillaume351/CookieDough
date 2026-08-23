package com.cookiebuild.cookiedough.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.ui.BedrockFormImages;

class HubGameMenuModelTest {
    @Test
    void everyLocaleGetsSevenReadableGameSubmenusWithImagesAndSafeActions() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.GERMAN,
                Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"), Locale.of("pa"))) {
            HubGameMenuModel index = HubGameMenuModel.index(locale);
            assertEquals(7, index.entries().size());
            for (HubGameMenuModel.Entry summary : index.entries()) {
                assertTrue(summary.action().startsWith("game:details:"));
                assertTrue(BedrockFormImages.isKnown(summary.bedrockTexture()));
                HubGameMenuModel detail = HubGameMenuModel.detail(
                        summary.action().substring("game:details:".length()), locale);
                assertEquals(List.of("game:join:", "games", "close"), List.of(
                        detail.entries().get(0).action().substring(0, "game:join:".length()),
                        detail.entries().get(1).action(), detail.entries().get(2).action()));
                assertTrue(detail.content().contains("\n"));
                assertFalse(detail.content().contains("game.rules."));
                assertTrue(detail.entries().stream()
                        .allMatch(entry -> BedrockFormImages.isKnown(entry.bedrockTexture())));
            }
        }
    }
}

package com.cookiebuild.cookiedough.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class PlatformTextTest {
    @Test
    void selectsTheBedrockVariantKey() {
        assertEquals("lobby.menu.subtitle", PlatformText.key("lobby.menu.subtitle", false));
        assertEquals("lobby.menu.subtitle.bedrock", PlatformText.key("lobby.menu.subtitle", true));
    }

    @Test
    void everyBedrockVariantHasAJavaBaseAndNeverAsksForAClick() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.GERMAN,
                Locale.ITALIAN, Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", locale,
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            long variants = bundle.keySet().stream().filter(key -> key.endsWith(PlatformText.BEDROCK_SUFFIX))
                    .peek(key -> assertTrue(bundle.containsKey(
                            key.substring(0, key.length() - PlatformText.BEDROCK_SUFFIX.length())), key))
                    .peek(key -> {
                        String value = bundle.getString(key).toLowerCase(Locale.ROOT);
                        assertFalse(value.contains("clic") || value.contains("click")
                                || value.contains("klick") || value.contains("clique"), locale + " " + key);
                    })
                    .count();
            assertTrue(variants >= 6, "Bedrock wording missing for " + locale);
        }
    }
}

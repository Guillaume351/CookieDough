package com.cookiebuild.cookiedough.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocaleManagerTest {

    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    void completeTranslationsMatchEnglishKeysAndPlaceholders() {
        ResourceBundle english = bundle(Locale.ENGLISH);

        for (Locale locale : Set.of(Locale.FRENCH, Locale.of("es"), Locale.of("pt", "BR"))) {
            ResourceBundle translated = bundle(locale);
            assertEquals(english.keySet(), translated.keySet(), "Missing or extra keys for " + locale);
            for (String key : english.keySet()) {
                assertEquals(
                        placeholders(english.getString(key)),
                        placeholders(translated.getString(key)),
                        "Placeholder mismatch for " + key + " in " + locale);
            }
        }
    }

    @Test
    void resolvesRegionalLocalesAndUsesExplicitEnglishFallback() {
        assertEquals(
                "Prática de reação iniciada. Sua vaga na fila está garantida: aguarde o JÁ e clique com o botão direito.",
                LocaleManager.getMessage("practice.started", Locale.of("pt", "PT")));
        assertEquals(
                "Reaction practice started. Your queue slot is safe: wait for GO!, then right-click.",
                LocaleManager.getMessage("practice.started", Locale.GERMAN));
        assertEquals(
                "Reaction practice started. Your queue slot is safe: wait for GO!, then right-click.",
                LocaleManager.getMessage("practice.started", Locale.JAPANESE));
        assertEquals(
                "Welcome to Cookie Build, Alex!",
                LocaleManager.getMessage("welcome.message", null, "Alex"));
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle(
                "messages",
                locale,
                LocaleManagerTest.class.getClassLoader(),
                NO_SYSTEM_LOCALE_FALLBACK);
    }

    private static Set<String> placeholders(String value) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }
}

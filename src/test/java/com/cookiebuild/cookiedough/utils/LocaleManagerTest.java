package com.cookiebuild.cookiedough.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
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
    private static final Pattern COLOR_CODE = Pattern.compile("§[0-9A-FK-OR]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROTECTED_TOKEN = Pattern.compile(
            "(?:https?://|www\\.)[A-Za-z0-9./_?=&%#-]+|@[A-Za-z0-9_]+|"
                    + "(?<![A-Za-z0-9])/[A-Za-z][A-Za-z0-9_-]*|\\\\n|\\\\u[0-9A-Fa-f]{4}");

    @Test
    void completeTranslationsMatchEnglishKeysAndPlaceholders() {
        ResourceBundle english = bundle(Locale.ENGLISH);

        for (Locale locale : Set.of(
                Locale.FRENCH,
                Locale.of("es"),
                Locale.of("pt", "BR"),
                Locale.of("bg"),
                Locale.of("hi"),
                Locale.GERMAN,
                Locale.ITALIAN)) {
            ResourceBundle translated = bundle(locale);
            assertEquals(english.keySet(), translated.keySet(), "Missing or extra keys for " + locale);
            for (String key : english.keySet()) {
                assertEquals(
                        placeholders(english.getString(key)),
                        placeholders(translated.getString(key)),
                        "Placeholder mismatch for " + key + " in " + locale);
                assertEquals(
                        tokens(COLOR_CODE, english.getString(key)),
                        tokens(COLOR_CODE, translated.getString(key)),
                        "Color-code mismatch for " + key + " in " + locale);
                assertEquals(
                        tokens(PROTECTED_TOKEN, english.getString(key)),
                        tokens(PROTECTED_TOKEN, translated.getString(key)),
                        "Protected-token mismatch for " + key + " in " + locale);
            }
        }
    }

    @Test
    void resolvesRegionalLocalesAndUsesExplicitEnglishFallback() {
        assertEquals(
                "Prática de reação iniciada. Sua vaga na fila está garantida: aguarde o JÁ e clique com o botão direito.",
                LocaleManager.getMessage("practice.started", Locale.of("pt", "PT")));
        assertEquals(
                "Cookie Build меню",
                LocaleManager.getMessage("hub.title", Locale.of("bg", "BG")));
        assertEquals(
                "Cookie Build मेनू",
                LocaleManager.getMessage("hub.title", Locale.of("hi", "IN")));
        assertEquals(
                "Reaktionstraining gestartet. Dein Warteschlangenplatz ist sicher: Warte auf LOS! und klicke dann mit der rechten Maustaste.",
                LocaleManager.getMessage("practice.started", Locale.GERMAN));
        assertEquals(
                "Allenamento di reazione avviato. Il tuo posto in coda è al sicuro: attendi GO!, poi fai clic con il tasto destro.",
                LocaleManager.getMessage("practice.started", Locale.ITALIAN));
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

    private static List<String> placeholders(String value) {
        return tokens(PLACEHOLDER, value);
    }

    private static List<String> tokens(Pattern pattern, String value) {
        List<String> placeholders = new ArrayList<>();
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        placeholders.sort(String::compareTo);
        return placeholders;
    }
}

package com.cookiebuild.cookiedough.utils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class LocaleManager {

    private static final String BASE_BUNDLE_NAME = "messages";
    private static final Locale BRAZILIAN_PORTUGUESE = Locale.of("pt", "BR");
    private static final List<Locale> AVAILABLE_BUNDLE_LOCALES = List.of(
            Locale.ENGLISH,
            Locale.FRENCH,
            Locale.of("es"),
            BRAZILIAN_PORTUGUESE,
            Locale.of("bg"),
            Locale.of("hi"),
            Locale.GERMAN,
            Locale.of("pa"),
            Locale.ITALIAN);
    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final Map<String, ResourceBundle> bundles = new ConcurrentHashMap<>();

    static {
        // Load the base messages
        loadBundle(BASE_BUNDLE_NAME);
    }

    private static void loadBundle(String bundleName) {
        for (Locale locale : AVAILABLE_BUNDLE_LOCALES) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(
                        bundleName,
                        locale,
                        LocaleManager.class.getClassLoader(),
                        NO_SYSTEM_LOCALE_FALLBACK);
                bundles.put(bundleName + "_" + locale.toString(), bundle);
            } catch (MissingResourceException e) {
                // Bundle not available for this locale
            }
        }
    }

    public static void registerBundle(String bundleName) {
        loadBundle(bundleName);
    }

    public static String getMessage(String key, Locale locale, Object... params) {
        ResourceBundle bundle = getBundleForLocale(BASE_BUNDLE_NAME, locale);
        if (bundle != null && bundle.containsKey(key)) {
            return formatMessage(bundle.getString(key), params);
        }
        ResourceBundle english = getBundleForLocale(BASE_BUNDLE_NAME, Locale.ENGLISH);
        if (english != null && english.containsKey(key)) {
            return formatMessage(english.getString(key), params);
        }
        return key; // Return the key itself if no translation is found
    }

    public static String getMessage(String bundleName, String key, Locale locale, Object... params) {
        ResourceBundle bundle = getBundleForLocale(bundleName, locale);
        if (bundle != null && bundle.containsKey(key)) {
            return formatMessage(bundle.getString(key), params);
        }
        ResourceBundle english = getBundleForLocale(bundleName, Locale.ENGLISH);
        if (english != null && english.containsKey(key)) {
            return formatMessage(english.getString(key), params);
        }
        return key; // Return the key itself if no translation is found
    }

    private static ResourceBundle getBundleForLocale(String bundleName, Locale locale) {
        Locale effectiveLocale = locale == null ? Locale.ENGLISH : locale;
        String bundleKey = bundleName + "_" + effectiveLocale.toString();
        ResourceBundle exact = bundles.get(bundleKey);
        if (exact != null) {
            return exact;
        }
        if ("pt".equals(effectiveLocale.getLanguage())) {
            ResourceBundle brazilianPortuguese = bundles.get(bundleName + "_pt_BR");
            if (brazilianPortuguese != null) {
                return brazilianPortuguese;
            }
        }
        ResourceBundle language = bundles.get(bundleName + "_" + effectiveLocale.getLanguage());
        return language != null ? language : bundles.get(bundleName + "_en");
    }

    private static String formatMessage(String message, Object... params) {
        for (int i = 0; i < params.length; i++) {
            message = message.replace("{" + i + "}", params[i].toString());
        }
        return message;
    }
}

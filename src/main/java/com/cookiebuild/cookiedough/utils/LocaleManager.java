package com.cookiebuild.cookiedough.utils;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.Map;

public class LocaleManager {

    private static final String BASE_BUNDLE_NAME = "messages";
    private static final Map<String, ResourceBundle> bundles = new HashMap<>();

    static {
        // Load the base messages
        loadBundle(BASE_BUNDLE_NAME);
    }

    private static void loadBundle(String bundleName) {
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(bundleName, locale);
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
        return key; // Return the key itself if no translation is found
    }

    public static String getMessage(String bundleName, String key, Locale locale, Object... params) {
        ResourceBundle bundle = getBundleForLocale(bundleName, locale);
        if (bundle != null && bundle.containsKey(key)) {
            return formatMessage(bundle.getString(key), params);
        }
        return key; // Return the key itself if no translation is found
    }

    private static ResourceBundle getBundleForLocale(String bundleName, Locale locale) {
        String bundleKey = bundleName + "_" + locale.toString();
        return bundles.getOrDefault(bundleKey, bundles.get(bundleName + "_en"));
    }

    private static String formatMessage(String message, Object... params) {
        for (int i = 0; i < params.length; i++) {
            message = message.replace("{" + i + "}", params[i].toString());
        }
        return message;
    }
}
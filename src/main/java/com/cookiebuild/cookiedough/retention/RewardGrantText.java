package com.cookiebuild.cookiedough.retention;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** Pure text helpers for website reward grants. */
public final class RewardGrantText {
    private RewardGrantText() {
    }

    /** Message key by grant source; unknown future sources get a generic text. */
    public static String messageKey(String source) {
        return switch (source == null ? "" : source) {
            case "app_link" -> "rewards.app.link";
            case "app_daily" -> "rewards.app.daily";
            // Weekly Build Battle gallery best-of (website job, Monday 10:00 Paris).
            case "bb_bestof" -> "rewards.bb.bestof";
            default -> "rewards.generic";
        };
    }

    /** "+150 coins + App badge" style list; language-neutral "+" separators. */
    public static String parts(int coins, int xp, String cosmeticName, IntFunction<String> coinText) {
        List<String> parts = new ArrayList<>();
        if (coins > 0) parts.add(coinText.apply(coins));
        if (xp > 0) parts.add("+" + xp + " XP");
        if (cosmeticName != null && !cosmeticName.isBlank()) parts.add(cosmeticName);
        return String.join(" + ", parts);
    }
}

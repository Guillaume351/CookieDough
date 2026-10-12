package com.cookiebuild.cookiedough.theme.hunt;

import java.util.UUID;

/** Idempotent rewards of a hunt (coins through the ledger, a reward cosmetic). */
public interface HuntRewards {
    /** Credits coins once per key; returns false if that key was already paid. */
    boolean coins(UUID playerId, String key, int amount);

    /** Grants a reward cosmetic; returns false if it could not be granted. */
    boolean cosmetic(UUID playerId, String cosmeticId, String source);
}

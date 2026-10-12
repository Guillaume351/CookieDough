package com.cookiebuild.cookiedough.theme.hunt;

import java.util.Set;
import java.util.UUID;

/** Durable record of the collectibles each player found (table {@code seasonal_collectibles}). */
public interface CollectibleRepository {
    Set<String> found(UUID playerId, String huntId);

    /** Records a find; returns false when the player had already found it. */
    boolean record(UUID playerId, String huntId, String collectibleId);
}

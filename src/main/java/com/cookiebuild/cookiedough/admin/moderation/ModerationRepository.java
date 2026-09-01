package com.cookiebuild.cookiedough.admin.moderation;

import java.util.List;
import java.util.UUID;

public interface ModerationRepository {
    List<ModerationAction> findActive(UUID playerId);

    ModerationAction create(ModerationAction action);

    int revoke(UUID playerId, ModerationAction.Type type, String revokedBy);
}

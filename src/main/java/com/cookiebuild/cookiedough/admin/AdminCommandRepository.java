package com.cookiebuild.cookiedough.admin;

import java.util.Optional;
import java.util.UUID;

interface AdminCommandRepository {
    Optional<AdminCommandResult> findTerminal(UUID commandId);

    void recordStarted(UUID commandId);

    void recordCompleted(UUID commandId, AdminCommandResult result);
}

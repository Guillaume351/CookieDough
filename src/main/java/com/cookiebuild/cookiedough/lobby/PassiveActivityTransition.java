package com.cookiebuild.cookiedough.lobby;

import java.util.function.BooleanSupplier;

/** Small transactional boundary used when moving between player-owned activities. */
final class PassiveActivityTransition {
    private PassiveActivityTransition() { }

    static boolean execute(BooleanSupplier targetPreflight, BooleanSupplier leaveSource,
            BooleanSupplier admitTarget, Runnable restoreSource) {
        if (!targetPreflight.getAsBoolean()) return false;
        if (!leaveSource.getAsBoolean()) {
            restoreSource.run();
            return false;
        }
        if (admitTarget.getAsBoolean()) return true;
        restoreSource.run();
        return false;
    }
}

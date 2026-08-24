package com.cookiebuild.cookiedough.listener;

/** Pure routing decision for a player loaded with a durable activity marker. */
record PersistentResumeRoute(boolean preserveState, boolean clearStaleMarker) {
    static PersistentResumeRoute resolve(boolean hasMarker, boolean registeredActivity, boolean ownedWorld) {
        if (hasMarker && !registeredActivity) {
            return new PersistentResumeRoute(false, true);
        }
        return new PersistentResumeRoute(registeredActivity || ownedWorld, false);
    }
}

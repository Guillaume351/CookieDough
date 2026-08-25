package com.cookiebuild.cookiedough.listener;

/** Pure routing decision for a player loaded with a durable activity marker. */
record PersistentResumeRoute(boolean preserveState, boolean clearStaleMarker) {
    private static final java.util.Set<String> KNOWN_PROVIDERS = java.util.Set.of("skyblock");

    static PersistentResumeRoute resolve(String marker, boolean registeredActivity, boolean ownedWorld) {
        boolean hasMarker = marker != null && !marker.isBlank();
        boolean knownProvider = hasMarker && KNOWN_PROVIDERS.contains(
                marker.toLowerCase(java.util.Locale.ROOT));
        if (hasMarker && !registeredActivity && !knownProvider) {
            return new PersistentResumeRoute(false, true);
        }
        return new PersistentResumeRoute(registeredActivity || ownedWorld || knownProvider, false);
    }
}

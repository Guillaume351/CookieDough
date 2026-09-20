package com.cookiebuild.cookiedough.cosmetics;

/** Once-per-connection local flair guard. */
final class SupporterJoinFlairPolicy {
    private SupporterJoinFlairPolicy() {
    }

    static boolean shouldPlay(boolean entitled, boolean inLobbyWorld, boolean alreadyPlayedThisSession) {
        return entitled && inLobbyWorld && !alreadyPlayedThisSession;
    }
}

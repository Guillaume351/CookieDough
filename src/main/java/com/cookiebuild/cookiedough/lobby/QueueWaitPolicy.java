package com.cookiebuild.cookiedough.lobby;

/**
 * Pure escalation rule for a player waiting in a queue that cannot start yet:
 * a help/options prompt at 20 s, then a one-tap consented switch at 45 s when
 * another queue already has players. Without such a queue the player simply
 * keeps waiting; nothing is ever switched without an explicit tap.
 */
final class QueueWaitPolicy {
    static final long OPTIONS_AFTER_SECONDS = 20L;
    static final long SWITCH_AFTER_SECONDS = 45L;
    static final long APP_PROMOTION_AFTER_SECONDS = 30L;

    enum Prompt {
        NONE,
        /** Several players wait for a composition: a light, non-modal hint. */
        HINT,
        /** Alone in the queue: switch, play Skyblock while waiting, practice or call players. */
        OPTIONS,
        /** Alone for a while and another queue has players: offer to move there. */
        SWITCH_OFFER
    }

    private QueueWaitPolicy() { }

    static Prompt next(long waitingSeconds, boolean alone, boolean optionsShown, boolean switchShown,
            boolean otherQueueHasPlayers) {
        if (alone && otherQueueHasPlayers && !switchShown && waitingSeconds >= SWITCH_AFTER_SECONDS) {
            return Prompt.SWITCH_OFFER;
        }
        if (!optionsShown && waitingSeconds >= OPTIONS_AFTER_SECONDS) {
            return alone ? Prompt.OPTIONS : Prompt.HINT;
        }
        return Prompt.NONE;
    }

    /** True when the switch candidate must be computed this tick (avoids per-second scans). */
    static boolean needsSwitchCandidate(long waitingSeconds, boolean alone, boolean optionsShown,
            boolean switchShown) {
        if (!alone) return false;
        return (!switchShown && waitingSeconds >= SWITCH_AFTER_SECONDS)
                || (!optionsShown && waitingSeconds >= OPTIONS_AFTER_SECONDS);
    }
}

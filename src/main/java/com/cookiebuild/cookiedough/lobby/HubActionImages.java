package com.cookiebuild.cookiedough.lobby;

import java.util.Map;
import java.util.Optional;

/** Semantic custom-pack paths for the first Bedrock lobby pages. */
public final class HubActionImages {
    private static final Map<String, String> TEXTURES = Map.ofEntries(
            Map.entry("quick", "actions/quick_play"),
            Map.entry("games", "actions/games"),
            Map.entry("goals", "actions/goals"),
            Map.entry("friends", "actions/friends"),
            Map.entry("party", "actions/party"),
            Map.entry("events", "actions/events"),
            Map.entry("app", "actions/app"),
            Map.entry("help", "actions/help"),
            Map.entry("community", "actions/friends"),
            Map.entry("back", "actions/back"));

    private HubActionImages() { }

    public static Optional<String> texture(String action) {
        return Optional.ofNullable(action == null ? null : TEXTURES.get(action));
    }
}

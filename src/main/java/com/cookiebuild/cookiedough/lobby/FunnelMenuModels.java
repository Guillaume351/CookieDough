package com.cookiebuild.cookiedough.lobby;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

import com.cookiebuild.cookiedough.utils.LocaleManager;

/**
 * Edition-neutral models for the activation funnel pages (post-match choice,
 * waiting options, consented queue switch, community links). Java renders
 * them as inventories and Bedrock as Cumulus forms, so no step relies on
 * clickable chat, which Bedrock cannot use.
 */
final class FunnelMenuModels {
    static final String DISCORD_INVITE = "discord.gg/ajmPnwh9g8";
    static final String APP_URL = "www.cookie-build.com";
    /** Public Build Battle gallery, always shown as plain text (Bedrock cannot open links). */
    static final String GALLERY_URL = "cookie-build.com/builds";

    /** Another queue a player could move to, with its current population. */
    record QueueChoice(String gameName, int players) { }

    private FunnelMenuModels() { }

    static HubGameMenuModel replay(String gameName, Locale locale, QueueChoice otherQueue, boolean autoReplay) {
        return replay(gameName, locale, otherQueue, autoReplay, null);
    }

    /**
     * @param returnLine optional "come back tomorrow" line (login calendar),
     *        shown under the status on both editions
     */
    static HubGameMenuModel replay(String gameName, Locale locale, QueueChoice otherQueue, boolean autoReplay,
            String returnLine) {
        String name = GamePresentation.readableName(gameName, locale);
        List<HubGameMenuModel.Entry> entries = new ArrayList<>();
        entries.add(new HubGameMenuModel.Entry("replay:same", Material.LIME_DYE, "actions/join",
                message("replay.menu.same", locale, name), message("replay.menu.same_hint", locale)));
        if (otherQueue != null && !otherQueue.gameName().equalsIgnoreCase(gameName)) {
            entries.add(new HubGameMenuModel.Entry("replay:join:" + otherQueue.gameName(), Material.COMPASS,
                    "actions/games", message("replay.menu.join_other", locale,
                            GamePresentation.readableName(otherQueue.gameName(), locale)),
                    message("replay.menu.join_other_hint", locale, otherQueue.players())));
        }
        entries.add(new HubGameMenuModel.Entry("replay:quick", Material.NETHER_STAR, "actions/quick_play",
                message("replay.menu.quick", locale), message("replay.menu.quick_hint", locale)));
        entries.add(new HubGameMenuModel.Entry("replay:auto", autoReplay ? Material.REPEATER : Material.COMPARATOR,
                "actions/upgrades", message(autoReplay ? "replay.menu.auto_on" : "replay.menu.auto_off", locale),
                message("replay.menu.auto_hint", locale)));
        entries.add(new HubGameMenuModel.Entry("replay:feedback", Material.WRITABLE_BOOK, "actions/help",
                message("feedback.action", locale), message("feedback.hover", locale)));
        entries.add(new HubGameMenuModel.Entry("replay:lobby", Material.OAK_DOOR, "actions/home",
                message("replay.menu.lobby", locale), message("replay.menu.lobby_hint", locale)));
        return new HubGameMenuModel(message("replay.menu.title", locale),
                message("replay.menu.status", locale, name) + "\n" + message("replay.menu.status_hint", locale)
                        + (returnLine == null || returnLine.isBlank() ? "" : "\n" + returnLine),
                entries);
    }

    /** "Galerie Build Battle": the URL as plain text, with a way back. */
    static HubGameMenuModel gallery(Locale locale) {
        List<HubGameMenuModel.Entry> entries = List.of(
                new HubGameMenuModel.Entry("back", Material.ARROW, "actions/back",
                        message("hub.back", locale), message("hub.back_lore", locale)),
                new HubGameMenuModel.Entry("close", Material.BARRIER, "actions/close",
                        message("hub.game.detail.close", locale), message("hub.game.detail.close_lore", locale)));
        return new HubGameMenuModel(message("hub.gallery.name", locale),
                message("hub.gallery.content", locale, GALLERY_URL), entries);
    }

    static HubGameMenuModel waiting(String gameName, int minimumPlayers, Locale locale, QueueChoice otherQueue,
            boolean skyblockAvailable) {
        String name = GamePresentation.readableName(gameName, locale);
        List<HubGameMenuModel.Entry> entries = new ArrayList<>();
        entries.add(new HubGameMenuModel.Entry("wait:stay", Material.CLOCK, "actions/join",
                message("queue.wait.stay", locale), message("queue.wait.stay_hint", locale, name)));
        if (otherQueue != null && !otherQueue.gameName().equalsIgnoreCase(gameName)) {
            entries.add(switchEntry(otherQueue, locale));
        }
        if (skyblockAvailable) {
            entries.add(new HubGameMenuModel.Entry("wait:activity:Skyblock", Material.GRASS_BLOCK, "modes/skyblock",
                    message("queue.wait.skyblock", locale), message("queue.wait.skyblock_hint", locale, name)));
        }
        entries.add(new HubGameMenuModel.Entry("wait:practice", Material.BLAZE_ROD, "actions/preview",
                message("queue.menu.practice", locale), message("queue.menu.practice_hint", locale)));
        entries.add(new HubGameMenuModel.Entry("wait:rally", Material.FIREWORK_ROCKET, "actions/friends",
                message("queue.menu.rally", locale), message("queue.menu.rally_hint", locale)));
        return new HubGameMenuModel(message("queue.wait.title", locale),
                message("queue.wait.content", locale, name, Math.max(2, minimumPlayers)), entries);
    }

    static HubGameMenuModel switchOffer(String gameName, Locale locale, QueueChoice otherQueue) {
        String name = GamePresentation.readableName(gameName, locale);
        String other = GamePresentation.readableName(otherQueue.gameName(), locale);
        List<HubGameMenuModel.Entry> entries = List.of(
                new HubGameMenuModel.Entry("wait:switch:" + otherQueue.gameName(), Material.LIME_DYE, "actions/join",
                        message("queue.switch.accept", locale, other),
                        message("queue.switch.accept_hint", locale, otherQueue.players())),
                new HubGameMenuModel.Entry("wait:stay", Material.CLOCK, "actions/back",
                        message("queue.switch.decline", locale, name), message("queue.wait.stay_hint", locale, name)));
        return new HubGameMenuModel(message("queue.switch.title", locale),
                message("queue.switch.content", locale, otherQueue.players(), other), entries);
    }

    static HubGameMenuModel community(Locale locale) {
        List<HubGameMenuModel.Entry> entries = List.of(
                new HubGameMenuModel.Entry("onboarding", Material.ARROW, "actions/back",
                        message("hub.back", locale), message("hub.back_lore", locale)),
                new HubGameMenuModel.Entry("close", Material.BARRIER, "actions/close",
                        message("hub.game.detail.close", locale), message("hub.game.detail.close_lore", locale)));
        return new HubGameMenuModel(message("hub.onboarding.community_name", locale),
                message("hub.community.plain", locale, DISCORD_INVITE, APP_URL), entries);
    }

    private static HubGameMenuModel.Entry switchEntry(QueueChoice otherQueue, Locale locale) {
        return new HubGameMenuModel.Entry("wait:switch:" + otherQueue.gameName(), Material.COMPASS, "actions/games",
                message("queue.wait.switch", locale, GamePresentation.readableName(otherQueue.gameName(), locale)),
                message("queue.wait.switch_hint", locale, otherQueue.players()));
    }

    private static String message(String key, Locale locale, Object... arguments) {
        return LocaleManager.getMessage(key, locale, arguments);
    }
}

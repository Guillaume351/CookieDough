package com.cookiebuild.cookiedough.cosmetics;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Visual prefixes of BADGE cosmetics. Bedrock clients do not render emoji
 * outside the Basic Multilingual Plane, so Bedrock viewers get a text label.
 */
public final class CosmeticBadges {
    private CosmeticBadges() {
    }

    /** Chat prefix as seen by one viewer. */
    public static Optional<Component> chatPrefix(String badgeId, boolean bedrockViewer) {
        if (CosmeticCatalog.SUPPORTER_BADGE.equals(badgeId)) {
            return Optional.of(Component.text("[Supporter] ", NamedTextColor.GOLD));
        }
        if (CosmeticCatalog.APP_COMPANION_BADGE.equals(badgeId)) {
            return Optional.of(Component.text(bedrockViewer ? "[App] " : "[📱] ", NamedTextColor.AQUA));
        }
        return Optional.empty();
    }

    /** Tab-list prefix; one component is shared by every viewer, so it stays text-only. */
    public static Optional<Component> tabPrefix(String badgeId) {
        if (CosmeticCatalog.SUPPORTER_BADGE.equals(badgeId)) {
            return Optional.of(Component.text("★ Supporter ", NamedTextColor.GOLD));
        }
        if (CosmeticCatalog.APP_COMPANION_BADGE.equals(badgeId)) {
            return Optional.of(Component.text("[App] ", NamedTextColor.AQUA));
        }
        return Optional.empty();
    }

    public static Component decorate(Component displayName, String badgeId, boolean bedrockViewer) {
        return chatPrefix(badgeId, bedrockViewer).map(prefix -> prefix.append(displayName)).orElse(displayName);
    }

    /** Viewer-aware wrapper around Paper's chat renderer; message and viewers are untouched. */
    public static ChatRenderer wrap(ChatRenderer delegate, Function<UUID, String> selectedBadge,
            Predicate<Object> bedrockViewer) {
        return (source, displayName, message, viewer) -> delegate.render(source,
                decorate(displayName, selectedBadge.apply(source.getUniqueId()), bedrockViewer.test(viewer)),
                message, viewer);
    }
}

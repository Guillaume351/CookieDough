package com.cookiebuild.cookiedough.cosmetics;

import java.util.UUID;
import java.util.function.Predicate;

import io.papermc.paper.chat.ChatRenderer;

/** Viewer-preserving wrapper for Paper's Adventure chat pipeline. */
public final class SupporterChatRenderer {
    private SupporterChatRenderer() {
    }

    public static ChatRenderer wrap(ChatRenderer delegate, Predicate<UUID> supporterSelection) {
        return (source, displayName, message, viewer) -> delegate.render(
                source,
                SupporterTitleFormatter.decorate(displayName,
                        supporterSelection.test(source.getUniqueId())),
                message,
                viewer);
    }
}

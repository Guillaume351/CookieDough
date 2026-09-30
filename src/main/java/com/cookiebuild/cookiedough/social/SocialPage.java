package com.cookiebuild.cookiedough.social;

import java.util.List;
import java.util.Objects;

/** Rendered, localized screen: Java lays entries out in a chest, Bedrock as form buttons. */
public record SocialPage(SocialPageId id, String title, List<String> content, List<Entry> entries) {
    public SocialPage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        content = List.copyOf(content);
        entries = List.copyOf(entries);
    }

    public enum Icon {
        FRIEND_ONLINE,
        FRIEND_OFFLINE,
        PLAYER,
        MEMBER,
        REQUEST,
        ADD,
        TYPE_NAME,
        PARTY,
        PARTY_INVITE,
        INVITE,
        JOIN_QUEUE,
        ACCEPT,
        DENY,
        REMOVE,
        LEAVE,
        FRIENDS,
        MORE,
        BACK,
        CLOSE
    }

    /**
     * @param headName   player whose skin the Java item may show (only when online)
     * @param decorative Java-only information item; Bedrock shows the same facts in the form content
     * @param footer     placed in the bottom row of the Java chest (back/close)
     */
    public record Entry(String label, String detail, Icon icon, SocialMenuAction action, String headName,
            boolean decorative, boolean footer) {
        public Entry {
            Objects.requireNonNull(label, "label");
            detail = detail == null ? "" : detail;
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(action, "action");
            headName = headName == null ? "" : headName;
        }
    }

    public List<Entry> bedrockButtons() {
        return entries.stream().filter(entry -> !entry.decorative()).toList();
    }
}

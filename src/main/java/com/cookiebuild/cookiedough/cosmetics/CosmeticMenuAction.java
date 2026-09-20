package com.cookiebuild.cookiedough.cosmetics;

import java.util.Optional;

/** Opaque action persisted in PDC or Bedrock button index maps. */
public record CosmeticMenuAction(Kind kind, CosmeticSlot slot, String cosmeticId) {
    public enum Kind {
        SELECT,
        DESELECT,
        PREVIEW_EMOTE,
        PREVIEW_VICTORY,
        LOCKED,
        NOOP
    }

    public static String select(CosmeticDefinition item) {
        return "select:" + item.slot().name() + ":" + item.id();
    }

    public static String deselect(CosmeticSlot slot) {
        return "deselect:" + slot.name();
    }

    public static String locked(CosmeticDefinition item) {
        return "locked:" + item.id();
    }

    public static Optional<CosmeticMenuAction> parse(String encoded) {
        if (encoded == null) return Optional.empty();
        String[] parts = encoded.split(":", -1);
        try {
            if (parts.length == 3 && "select".equals(parts[0])) {
                CosmeticSlot slot = CosmeticSlot.valueOf(parts[1]);
                return CosmeticCatalog.find(parts[2])
                        .filter(item -> item.slot() == slot)
                        .map(item -> new CosmeticMenuAction(Kind.SELECT, slot, item.id()));
            }
            if (parts.length == 2 && "deselect".equals(parts[0])) {
                return Optional.of(new CosmeticMenuAction(
                        Kind.DESELECT, CosmeticSlot.valueOf(parts[1]), null));
            }
            if (parts.length == 2 && "locked".equals(parts[0])
                    && CosmeticCatalog.find(parts[1]).isPresent()) {
                return Optional.of(new CosmeticMenuAction(Kind.LOCKED, null, parts[1]));
            }
            if (parts.length == 1 && "preview_emote".equals(parts[0])) {
                return Optional.of(new CosmeticMenuAction(
                        Kind.PREVIEW_EMOTE, CosmeticSlot.EMOTE, CosmeticCatalog.COOKIE_CHEER));
            }
            if (parts.length == 1 && "preview_victory".equals(parts[0])) {
                return Optional.of(new CosmeticMenuAction(
                        Kind.PREVIEW_VICTORY, CosmeticSlot.VICTORY_EFFECT,
                        CosmeticCatalog.GOLDEN_COOKIE_BURST));
            }
            if (parts.length == 1 && "noop".equals(parts[0])) {
                return Optional.of(new CosmeticMenuAction(Kind.NOOP, null, null));
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown slot: fail closed.
        }
        return Optional.empty();
    }
}

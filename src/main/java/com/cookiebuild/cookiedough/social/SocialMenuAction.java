package com.cookiebuild.cookiedough.social;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One button of the social menu, encoded as {@code kind} or {@code kind:argument}
 * in a Java item PDC tag or kept server-side for a Bedrock form button.
 */
public record SocialMenuAction(Kind kind, String argument) {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_ .-]{1,32}");

    enum Argument { NONE, PLAYER, UUID }

    public enum Kind {
        HOME(SocialPageId.HOME, Argument.NONE),
        FRIENDS(SocialPageId.FRIENDS, Argument.NONE),
        FRIEND(SocialPageId.FRIEND, Argument.PLAYER),
        REQUESTS(SocialPageId.REQUESTS, Argument.NONE),
        REQUEST(SocialPageId.REQUEST, Argument.PLAYER),
        ADD(SocialPageId.ADD, Argument.NONE),
        PARTY(SocialPageId.PARTY, Argument.NONE),
        PARTY_INVITE(SocialPageId.PARTY_INVITE, Argument.NONE),
        CONFIRM_REMOVE(SocialPageId.CONFIRM_REMOVE, Argument.PLAYER),
        CONFIRM_LEAVE(SocialPageId.CONFIRM_LEAVE, Argument.NONE),
        ACCEPT(null, Argument.PLAYER),
        DENY(null, Argument.PLAYER),
        SEND_REQUEST(null, Argument.PLAYER),
        ADD_BY_NAME(null, Argument.NONE),
        INVITE(null, Argument.PLAYER),
        JOIN_PARTY(null, Argument.UUID),
        JOIN_QUEUE(null, Argument.PLAYER),
        REMOVE(null, Argument.PLAYER),
        LEAVE(null, Argument.NONE),
        CLOSE(null, Argument.NONE),
        NOOP(null, Argument.NONE);

        private final SocialPageId page;
        private final Argument argument;

        Kind(SocialPageId page, Argument argument) {
            this.page = page;
            this.argument = argument;
        }

        /** The page this navigation button opens, or null for a mutation/close. */
        public SocialPageId page() {
            return page;
        }

        String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public SocialMenuAction {
        Objects.requireNonNull(kind, "kind");
        argument = argument == null ? "" : argument;
        if (!valid(kind.argument, argument)) {
            throw new IllegalArgumentException("Invalid argument for " + kind + ": " + argument);
        }
    }

    public static SocialMenuAction of(Kind kind) {
        return new SocialMenuAction(kind, "");
    }

    public static SocialMenuAction of(Kind kind, String argument) {
        return new SocialMenuAction(kind, argument);
    }

    public boolean navigation() {
        return kind.page != null;
    }

    public String encode() {
        return argument.isEmpty() ? kind.token() : kind.token() + ":" + argument;
    }

    public UUID uuidArgument() {
        return kind.argument == Argument.UUID ? UUID.fromString(argument) : null;
    }

    public static Optional<SocialMenuAction> parse(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 64) return Optional.empty();
        int separator = encoded.indexOf(':');
        String token = separator < 0 ? encoded : encoded.substring(0, separator);
        String argument = separator < 0 ? "" : encoded.substring(separator + 1);
        for (Kind kind : Kind.values()) {
            if (kind.token().equals(token)) {
                return valid(kind.argument, argument) ? Optional.of(new SocialMenuAction(kind, argument))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean valid(Argument expected, String argument) {
        return switch (expected) {
            case NONE -> argument.isEmpty();
            case PLAYER -> PLAYER_NAME.matcher(argument).matches();
            case UUID -> {
                try {
                    java.util.UUID.fromString(argument);
                    yield true;
                } catch (IllegalArgumentException invalid) {
                    yield false;
                }
            }
        };
    }
}

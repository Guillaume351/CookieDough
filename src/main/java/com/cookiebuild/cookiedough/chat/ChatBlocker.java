package com.cookiebuild.cookiedough.chat;

/**
 * Defines a blocker for a specific pattern.
 */
public class ChatBlocker {
    private String pattern;

    public ChatBlocker(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public boolean matches(String message) {
        return message.contains(pattern);
    }
}

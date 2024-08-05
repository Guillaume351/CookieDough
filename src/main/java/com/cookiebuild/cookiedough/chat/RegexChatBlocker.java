package com.cookiebuild.cookiedough.chat;

import java.util.regex.Pattern;

/**
 * Chat blocker that uses regex patterns.
 */
public class RegexChatBlocker extends ChatBlocker {
    private Pattern pattern;

    public RegexChatBlocker(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public boolean matches(String message) {
        return pattern.matcher(message).find();
    }
}
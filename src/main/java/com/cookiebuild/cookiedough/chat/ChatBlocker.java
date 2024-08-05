package com.cookiebuild.cookiedough.chat;

/**
 * Abstract class for a generic chat blocker.
 */
public abstract class ChatBlocker {
    public abstract boolean matches(String message);
}
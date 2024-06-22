package com.cookiebuild.cookiedough.chat;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Chat blocker that blocks spam based on message frequency.
 */
public class SpamBlocker extends ChatBlocker {
    private static final long TIME_WINDOW = 10000; // 10 seconds
    private static final int MAX_MESSAGES = 5;

    private Queue<Long> messageTimestamps = new LinkedList<>();

    @Override
    public boolean matches(String message) {
        long currentTime = System.currentTimeMillis();

        // Remove timestamps outside of the time window
        while (!messageTimestamps.isEmpty() && currentTime - messageTimestamps.peek() > TIME_WINDOW) {
            messageTimestamps.poll();
        }

        // Check if the number of messages in the time window exceeds the limit
        if (messageTimestamps.size() >= MAX_MESSAGES) {
            return true;
        }

        // Add current timestamp
        messageTimestamps.add(currentTime);
        return false;
    }
}
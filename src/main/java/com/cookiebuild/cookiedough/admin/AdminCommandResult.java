package com.cookiebuild.cookiedough.admin;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record AdminCommandResult(String status, ObjectNode result, String error) {
    public static AdminCommandResult completed(ObjectNode result) {
        return new AdminCommandResult("completed", result == null ? JsonNodeFactory.instance.objectNode() : result, null);
    }

    public static AdminCommandResult failed(String error) {
        return new AdminCommandResult("failed", JsonNodeFactory.instance.objectNode(), safeError(error));
    }

    public static AdminCommandResult expired() {
        return new AdminCommandResult("expired", JsonNodeFactory.instance.objectNode(), "Command expired before execution");
    }

    public boolean succeeded() {
        return "completed".equals(status);
    }

    private static String safeError(String error) {
        if (error == null || error.isBlank()) return "Command failed";
        String trimmed = error.replaceAll("[\\r\\n\\t]", " ").trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}

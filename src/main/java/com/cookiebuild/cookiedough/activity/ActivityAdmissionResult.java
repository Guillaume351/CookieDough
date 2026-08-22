package com.cookiebuild.cookiedough.activity;

/** Stable result returned by a persistent activity admission attempt. */
public record ActivityAdmissionResult(boolean admitted, String message) {
    public ActivityAdmissionResult {
        message = message == null ? "" : message;
    }

    public static ActivityAdmissionResult admitted(String message) {
        return new ActivityAdmissionResult(true, message);
    }

    public static ActivityAdmissionResult rejected(String message) {
        return new ActivityAdmissionResult(false, message);
    }
}

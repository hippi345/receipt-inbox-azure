package com.interview.receipt.logging;

import java.nio.file.Path;

public final class ActivityLog {
    public static final String APPENDER_NAME = "receipt-activity";

    private ActivityLog() {
    }

    public static Path file() {
        String override = System.getenv("RECEIPT_ACTIVITY_LOG");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".receipt-inbox", "activity.log");
    }
}

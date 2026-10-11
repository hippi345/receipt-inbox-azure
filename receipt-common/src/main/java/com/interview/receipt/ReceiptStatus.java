package com.interview.receipt;

public enum ReceiptStatus {
    RECEIVED("received"),
    PROCESSED("processed"),
    FAILED("failed");

    private final String value;

    ReceiptStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ReceiptStatus fromValue(String value) {
        for (ReceiptStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown receipt status: " + value);
    }
}

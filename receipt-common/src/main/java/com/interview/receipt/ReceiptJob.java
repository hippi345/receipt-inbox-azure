package com.interview.receipt;

public record ReceiptJob(String receiptId, String crid, String traceparent) {
    public ReceiptJob(String receiptId) {
        this(receiptId, null, null);
    }
}

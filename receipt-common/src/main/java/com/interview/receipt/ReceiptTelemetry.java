package com.interview.receipt;

public interface ReceiptTelemetry {
    void processed(String receiptId);
    void failed(String receiptId, String reason);
    void cacheError(String receiptId, RuntimeException error);
}

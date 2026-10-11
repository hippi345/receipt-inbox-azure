package com.interview.receipt;

public class NoopReceiptTelemetry implements ReceiptTelemetry {
    @Override
    public void processed(String receiptId) {
    }

    @Override
    public void failed(String receiptId, String reason) {
    }

    @Override
    public void cacheError(String receiptId, RuntimeException error) {
    }
}

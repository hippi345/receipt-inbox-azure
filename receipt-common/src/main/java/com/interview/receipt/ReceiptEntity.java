package com.interview.receipt;

import java.time.Instant;

public record ReceiptEntity(
        String id,
        ReceiptStatus status,
        String fileName,
        String blobPath,
        String merchant,
        String amount,
        String notes,
        Instant createdAt,
        Instant processedAt,
        String failureReason
) {
    public ReceiptEntity processed(Instant at) {
        return new ReceiptEntity(id, ReceiptStatus.PROCESSED, fileName, blobPath, merchant, amount, notes, createdAt, at, null);
    }

    public ReceiptEntity failed(Instant at, String reason) {
        return new ReceiptEntity(id, ReceiptStatus.FAILED, fileName, blobPath, merchant, amount, notes, createdAt, at, reason);
    }
}

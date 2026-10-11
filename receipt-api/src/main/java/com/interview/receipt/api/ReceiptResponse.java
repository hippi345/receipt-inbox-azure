package com.interview.receipt.api;

import com.interview.receipt.ReceiptEntity;

import java.time.Instant;

public record ReceiptResponse(
        String id,
        String status,
        String fileName,
        String merchant,
        String amount,
        String notes,
        Instant createdAt,
        Instant processedAt,
        String failureReason) {

    public static ReceiptResponse from(ReceiptEntity entity) {
        return new ReceiptResponse(
                entity.id(),
                entity.status().value(),
                entity.fileName(),
                entity.merchant(),
                entity.amount(),
                entity.notes(),
                entity.createdAt(),
                entity.processedAt(),
                entity.failureReason());
    }
}

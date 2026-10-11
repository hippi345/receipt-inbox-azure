package com.interview.receipt;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReceiptEntityTest {

    @Test
    void statusRoundTripsThroughItsStorageValue() {
        assertEquals(ReceiptStatus.RECEIVED, ReceiptStatus.fromValue("received"));
        assertEquals("processed", ReceiptStatus.PROCESSED.value());
    }

    @Test
    void processedCopyKeepsTheUploadFields() {
        Instant created = Instant.parse("2026-10-08T12:00:00Z");
        ReceiptEntity received = new ReceiptEntity(
                "id-1", ReceiptStatus.RECEIVED, "receipt.pdf", "receipts/id-1/receipt.pdf",
                "Cafe", "12.50", "lunch", created, null, null);

        ReceiptEntity processed = received.processed(Instant.parse("2026-10-08T12:01:00Z"));

        assertEquals(ReceiptStatus.PROCESSED, processed.status());
        assertEquals("Cafe", processed.merchant());
        assertEquals("12.50", processed.amount());
        assertEquals(Instant.parse("2026-10-08T12:01:00Z"), processed.processedAt());
        assertNull(processed.failureReason());
    }

    @Test
    void failedCopyRecordsTheReason() {
        ReceiptEntity received = new ReceiptEntity(
                "id-1", ReceiptStatus.RECEIVED, "receipt.pdf", "receipts/id-1/receipt.pdf",
                null, null, null, Instant.parse("2026-10-08T12:00:00Z"), null, null);

        ReceiptEntity failed = received.failed(Instant.parse("2026-10-08T12:02:00Z"), "queue send failed");

        assertEquals(ReceiptStatus.FAILED, failed.status());
        assertEquals("queue send failed", failed.failureReason());
    }
}

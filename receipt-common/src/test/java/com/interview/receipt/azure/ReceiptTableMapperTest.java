package com.interview.receipt.azure;

import com.azure.data.tables.models.TableEntity;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReceiptTableMapperTest {

    @Test
    void roundTripsEveryFieldAndOmitsNulls() {
        Instant created = Instant.parse("2026-10-08T12:00:00Z");
        Instant processed = Instant.parse("2026-10-08T12:05:00Z");
        ReceiptEntity populated = new ReceiptEntity(
                "id-1",
                ReceiptStatus.FAILED,
                "receipt.pdf",
                "receipts/id-1/receipt.pdf",
                "Cafe",
                "12.50",
                "lunch",
                created,
                processed,
                "queue send failed");

        TableEntity table = ReceiptTableMapper.toTable(populated);

        assertEquals("receipt", table.getPartitionKey());
        assertEquals("id-1", table.getRowKey());
        assertEquals("failed", table.getProperty("status"));
        assertEquals("receipt.pdf", table.getProperty("fileName"));
        assertEquals("receipts/id-1/receipt.pdf", table.getProperty("blobPath"));
        assertEquals("Cafe", table.getProperty("merchant"));
        assertEquals("12.50", table.getProperty("amount"));
        assertEquals("lunch", table.getProperty("notes"));
        assertEquals("2026-10-08T12:00:00Z", table.getProperty("createdAt"));
        assertEquals("2026-10-08T12:05:00Z", table.getProperty("processedAt"));
        assertEquals("queue send failed", table.getProperty("failureReason"));
        assertEquals(populated, ReceiptTableMapper.fromTable(table));

        ReceiptEntity received = new ReceiptEntity(
                "id-2",
                ReceiptStatus.RECEIVED,
                "note.txt",
                "receipts/id-2/note.txt",
                null,
                null,
                null,
                created,
                null,
                null);
        TableEntity sparse = ReceiptTableMapper.toTable(received);

        assertEquals("receipt", sparse.getPartitionKey());
        assertEquals("id-2", sparse.getRowKey());
        assertEquals("received", sparse.getProperty("status"));
        assertEquals("2026-10-08T12:00:00Z", sparse.getProperty("createdAt"));
        assertFalse(sparse.getProperties().containsKey("merchant"));
        assertFalse(sparse.getProperties().containsKey("amount"));
        assertFalse(sparse.getProperties().containsKey("notes"));
        assertFalse(sparse.getProperties().containsKey("processedAt"));
        assertFalse(sparse.getProperties().containsKey("failureReason"));

        ReceiptEntity restored = ReceiptTableMapper.fromTable(sparse);
        assertNull(restored.merchant());
        assertNull(restored.amount());
        assertNull(restored.notes());
        assertNull(restored.processedAt());
        assertNull(restored.failureReason());
        assertEquals(received, restored);
    }
}

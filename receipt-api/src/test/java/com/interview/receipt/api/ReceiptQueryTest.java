package com.interview.receipt.api;

import com.interview.receipt.NoopReceiptTelemetry;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptStatus;
import com.interview.receipt.memory.InMemoryReceiptCache;
import com.interview.receipt.memory.InMemoryReceiptTable;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceiptQueryTest {

    @Test
    void cacheHitDoesNotReadTheTable() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        ReceiptEntity processed = received("id-1").processed(Instant.parse("2026-10-08T12:01:00Z"));
        cache.putProcessed(processed);
        ReceiptQuery query = new ReceiptQuery(table, cache, new NoopReceiptTelemetry(), true);

        assertEquals(ReceiptStatus.PROCESSED, query.get("id-1").status());
        assertEquals(0, table.readCount());
        assertEquals(1, query.list().size());
        assertEquals(0, table.readCount());
    }

    @Test
    void cacheMissReadsTheTable() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        ReceiptQuery query = new ReceiptQuery(table, new InMemoryReceiptCache(), new NoopReceiptTelemetry(), true);

        assertEquals(ReceiptStatus.RECEIVED, query.get("id-1").status());
        assertEquals(1, table.readCount());
    }

    @Test
    void redisFailureStillReturnsTheTableRow() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        cache.failNext();
        ReceiptQuery query = new ReceiptQuery(table, cache, new NoopReceiptTelemetry(), true);

        assertEquals("id-1", query.get("id-1").id());
    }

    @Test
    void disabledRedisDoesNotTouchTheCache() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        cache.failNext();
        ReceiptQuery query = new ReceiptQuery(table, cache, new NoopReceiptTelemetry(), false);

        assertEquals("id-1", query.list().getFirst().id());
    }

    @Test
    void missingReceiptIsNotFound() {
        ReceiptQuery query = new ReceiptQuery(new InMemoryReceiptTable(), new InMemoryReceiptCache(), new NoopReceiptTelemetry(), false);
        assertThrows(ReceiptNotFoundException.class, () -> query.get("missing"));
    }

    private static ReceiptEntity received(String id) {
        return new ReceiptEntity(id, ReceiptStatus.RECEIVED, id + ".pdf", "receipts/" + id + "/" + id + ".pdf",
                null, null, null, Instant.parse("2026-10-08T12:00:00Z"), null, null);
    }
}

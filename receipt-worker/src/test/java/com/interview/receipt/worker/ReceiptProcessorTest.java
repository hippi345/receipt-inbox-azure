package com.interview.receipt.worker;

import com.interview.receipt.QueuedReceipt;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptJob;
import com.interview.receipt.ReceiptStatus;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.ReceiptTelemetry;
import com.interview.receipt.memory.InMemoryReceiptCache;
import com.interview.receipt.memory.InMemoryReceiptJobs;
import com.interview.receipt.memory.InMemoryReceiptTable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void receivedRowWithOneDequeueBecomesProcessed() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        QueuedReceipt message = message("id-1", 1);
        jobs.release(message);

        processor(table, jobs, cache, telemetry).handle(message);

        ReceiptEntity row = table.find("id-1");
        assertEquals(ReceiptStatus.PROCESSED, row.status());
        assertEquals(NOW, row.processedAt());
        assertEquals("Cafe", row.merchant());
        assertEquals("9.00", row.amount());
        assertEquals("lunch", row.notes());
        assertNull(row.failureReason());
        assertEquals(row, cache.find("id-1").orElseThrow());
        assertEquals(0, jobs.queuedCount());
        assertTrue(jobs.poisoned().isEmpty());
        assertEquals(1, telemetry.processedCount());
        assertEquals(0, telemetry.failedCount());
    }

    @Test
    void alreadyProcessedRowDeletesTheMessageWithoutWriting() {
        Instant originalProcessedAt = Instant.parse("2026-10-08T12:01:00Z");
        InMemoryReceiptTable stored = new InMemoryReceiptTable();
        stored.insert(received("id-1").processed(originalProcessedAt));
        UpdateCountingTable table = new UpdateCountingTable(stored);
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        QueuedReceipt message = message("id-1", 2);
        jobs.release(message);

        processor(table, jobs, cache, telemetry).handle(message);

        ReceiptEntity row = table.find("id-1");
        assertEquals(ReceiptStatus.PROCESSED, row.status());
        assertEquals(originalProcessedAt, row.processedAt());
        assertEquals(0, table.updateCount());
        assertTrue(cache.find("id-1").isEmpty());
        assertEquals(0, jobs.queuedCount());
        assertTrue(jobs.poisoned().isEmpty());
        assertEquals(0, telemetry.processedCount());
        assertEquals(0, telemetry.failedCount());
    }

    @Test
    void fifthDequeueFailsTheRowAndPoisonsTheMessage() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        QueuedReceipt message = message("id-1", 5);
        jobs.release(message);

        processor(table, jobs, cache, telemetry).handle(message);

        ReceiptEntity row = table.find("id-1");
        assertEquals(ReceiptStatus.FAILED, row.status());
        assertEquals(NOW, row.processedAt());
        assertEquals("poisoned after 5 dequeues", row.failureReason());
        assertEquals(List.of(message), jobs.poisoned());
        assertEquals(0, jobs.queuedCount());
        assertTrue(cache.find("id-1").isEmpty());
        assertEquals(0, telemetry.processedCount());
        assertEquals(1, telemetry.failedCount());
        assertEquals("poisoned after 5 dequeues", telemetry.failedReason());
    }

    @Test
    void redisFailureStillProcessesTheRowAndDeletesTheMessage() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        cache.failNext();
        RecordingTelemetry telemetry = new RecordingTelemetry();
        QueuedReceipt message = message("id-1", 1);
        jobs.release(message);

        processor(table, jobs, cache, telemetry).handle(message);

        ReceiptEntity row = table.find("id-1");
        assertEquals(ReceiptStatus.PROCESSED, row.status());
        assertEquals(NOW, row.processedAt());
        assertNull(row.failureReason());
        assertTrue(cache.find("id-1").isEmpty());
        assertEquals(0, jobs.queuedCount());
        assertTrue(jobs.poisoned().isEmpty());
        assertEquals(1, telemetry.processedCount());
        assertEquals(1, telemetry.cacheErrorCount());
        assertEquals(0, telemetry.failedCount());
    }

    private ReceiptProcessor processor(
            ReceiptTable table,
            InMemoryReceiptJobs jobs,
            InMemoryReceiptCache cache,
            RecordingTelemetry telemetry) {
        return new ReceiptProcessor(table, jobs, cache, telemetry, clock, true);
    }

    private static ReceiptEntity received(String id) {
        return new ReceiptEntity(
                id,
                ReceiptStatus.RECEIVED,
                "lunch.pdf",
                "receipts/" + id + "/lunch.pdf",
                "Cafe",
                "9.00",
                "lunch",
                Instant.parse("2026-10-08T12:00:00Z"),
                null,
                null);
    }

    private static QueuedReceipt message(String receiptId, int dequeueCount) {
        return new QueuedReceipt("msg-" + receiptId, "pop-" + receiptId, dequeueCount, new ReceiptJob(receiptId));
    }

    private static final class UpdateCountingTable implements ReceiptTable {
        private final InMemoryReceiptTable delegate;
        private int updates;

        private UpdateCountingTable(InMemoryReceiptTable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insert(ReceiptEntity entity) {
            delegate.insert(entity);
        }

        @Override
        public ReceiptEntity find(String id) {
            return delegate.find(id);
        }

        @Override
        public void update(ReceiptEntity entity) {
            updates++;
            delegate.update(entity);
        }

        @Override
        public List<ReceiptEntity> listNewest(int limit) {
            return delegate.listNewest(limit);
        }

        private int updateCount() {
            return updates;
        }
    }

    private static final class RecordingTelemetry implements ReceiptTelemetry {
        private int processed;
        private int failed;
        private String failedReason;
        private int cacheErrors;

        @Override
        public void processed(String receiptId) {
            processed++;
        }

        @Override
        public void failed(String receiptId, String reason) {
            failed++;
            failedReason = reason;
        }

        @Override
        public void cacheError(String receiptId, RuntimeException error) {
            cacheErrors++;
        }

        private int processedCount() {
            return processed;
        }

        private int failedCount() {
            return failed;
        }

        private String failedReason() {
            return failedReason;
        }

        private int cacheErrorCount() {
            return cacheErrors;
        }
    }
}

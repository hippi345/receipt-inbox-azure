package com.interview.receipt.api;

import com.interview.receipt.ReceiptStatus;
import com.interview.receipt.memory.InMemoryReceiptBlobs;
import com.interview.receipt.memory.InMemoryReceiptJobs;
import com.interview.receipt.memory.InMemoryReceiptTable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptIntakeTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void submitStoresBlobRowAndQueueMessage() {
        InMemoryReceiptBlobs blobs = new InMemoryReceiptBlobs();
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        ReceiptIntake intake = new ReceiptIntake(blobs, table, jobs, clock);

        var saved = intake.submit("lunch.pdf", "application/pdf", new byte[]{1, 2}, "Cafe", "9.00", "lunch");

        assertEquals(ReceiptStatus.RECEIVED, saved.status());
        assertTrue(blobs.contains(saved.blobPath()));
        assertEquals(saved.id(), table.find(saved.id()).id());
        assertEquals(saved.id(), jobs.receive().orElseThrow().job().receiptId());
    }

    @Test
    void tableFailureDeletesTheBlob() {
        InMemoryReceiptBlobs blobs = new InMemoryReceiptBlobs();
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.failNextInsert();
        ReceiptIntake intake = new ReceiptIntake(blobs, table, new InMemoryReceiptJobs(), clock);

        assertThrows(ReceiptStorageException.class,
                () -> intake.submit("lunch.pdf", "application/pdf", new byte[]{1}, null, null, null));
        assertFalse(blobs.containsAny());
    }

    @Test
    void queueFailureMarksTheRowFailed() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        jobs.failNextSend();
        ReceiptIntake intake = new ReceiptIntake(new InMemoryReceiptBlobs(), table, jobs, clock);

        ReceiptStorageException error = assertThrows(ReceiptStorageException.class,
                () -> intake.submit("lunch.pdf", "application/pdf", new byte[]{1}, null, null, null));

        assertEquals(ReceiptStatus.FAILED, table.listNewest(1).getFirst().status());
        assertEquals("queue send failed", table.listNewest(1).getFirst().failureReason());
        assertTrue(error.getMessage().contains("queue"));
    }
}

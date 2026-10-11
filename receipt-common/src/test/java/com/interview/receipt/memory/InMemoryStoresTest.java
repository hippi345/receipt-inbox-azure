package com.interview.receipt.memory;

import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptJob;
import com.interview.receipt.ReceiptStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStoresTest {

    @Test
    void tableListsNewestFirstAndCountsReads() {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(sample("older", "2026-10-08T12:00:00Z"));
        table.insert(sample("newer", "2026-10-08T13:00:00Z"));

        assertEquals("newer", table.listNewest(1).getFirst().id());
        assertEquals(1, table.readCount());
    }

    @Test
    void jobsReceiveIncrementsDequeueCountAndPoisonKeepsTheMessage() {
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        jobs.send(new ReceiptJob("id-1"));

        var first = jobs.receive().orElseThrow();
        jobs.release(first);
        var second = jobs.receive().orElseThrow();

        assertEquals(2, second.dequeueCount());
        jobs.poison(second);
        assertEquals(1, jobs.poisoned().size());
        assertTrue(jobs.receive().isEmpty());
    }

    @Test
    void cacheStoresAProcessedReceiptAndTheRecentList() {
        InMemoryReceiptCache cache = new InMemoryReceiptCache();
        assertTrue(cache.recent().isEmpty());

        cache.putProcessed(sample("id-1", "2026-10-08T12:00:00Z").processed(Instant.parse("2026-10-08T12:01:00Z")));

        Optional<ReceiptEntity> found = cache.find("id-1");
        assertTrue(found.isPresent());
        assertEquals(1, cache.recent().orElseThrow().size());
    }

    private static ReceiptEntity sample(String id, String created) {
        return new ReceiptEntity(id, ReceiptStatus.RECEIVED, id + ".pdf", "receipts/" + id + "/" + id + ".pdf",
                "Cafe", "4.00", null, Instant.parse(created), null, null);
    }
}

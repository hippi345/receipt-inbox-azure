package com.interview.receipt.worker;

import com.interview.receipt.NoopReceiptTelemetry;
import com.interview.receipt.QueuedReceipt;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptJob;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.ReceiptStatus;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.logging.Correlation;
import com.interview.receipt.memory.InMemoryReceiptCache;
import com.interview.receipt.memory.InMemoryReceiptJobs;
import com.interview.receipt.memory.InMemoryReceiptTable;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerLoopTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");

    @Test
    void processesOneQueuedReceiptThenKeepsPolling() throws Exception {
        InMemoryReceiptTable table = new InMemoryReceiptTable();
        table.insert(received("id-1"));
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        jobs.send(new ReceiptJob("id-1"));
        ReceiptProcessor processor = new ReceiptProcessor(
                table,
                jobs,
                new InMemoryReceiptCache(),
                new NoopReceiptTelemetry(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                false);

        Thread worker = start(new WorkerLoop(jobs, processor, OpenTelemetry.noop()));
        await(() -> table.find("id-1").status() == ReceiptStatus.PROCESSED);
        stop(worker);

        assertEquals(ReceiptStatus.PROCESSED, table.find("id-1").status());
        assertEquals(0, jobs.queuedCount());
    }

    @Test
    void keepsRunningWhenHandleThrowsBeforeDelete() throws Exception {
        ReceiptTable table = new ReceiptTable() {
            @Override
            public void insert(ReceiptEntity entity) {
            }

            @Override
            public ReceiptEntity find(String id) {
                throw new RuntimeException("find failed");
            }

            @Override
            public void update(ReceiptEntity entity) {
            }

            @Override
            public List<ReceiptEntity> listNewest(int limit) {
                return List.of();
            }
        };
        InMemoryReceiptJobs jobs = new InMemoryReceiptJobs();
        jobs.send(new ReceiptJob("id-1"));
        AtomicInteger receives = new AtomicInteger();
        ReceiptJobs countingJobs = new ReceiptJobs() {
            @Override
            public void send(ReceiptJob job) {
                jobs.send(job);
            }

            @Override
            public Optional<QueuedReceipt> receive() {
                receives.incrementAndGet();
                return jobs.receive();
            }

            @Override
            public void delete(QueuedReceipt message) {
                jobs.delete(message);
            }

            @Override
            public void poison(QueuedReceipt message) {
                jobs.poison(message);
            }
        };
        ReceiptProcessor processor = new ReceiptProcessor(
                table,
                countingJobs,
                new InMemoryReceiptCache(),
                new NoopReceiptTelemetry(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                false);

        Thread worker = start(new WorkerLoop(countingJobs, processor, OpenTelemetry.noop()));
        await(() -> receives.get() >= 1);
        Thread.sleep(50);
        assertTrue(worker.isAlive());
        stop(worker);

        assertEquals(0, jobs.queuedCount());
        assertTrue(jobs.poisoned().isEmpty());
    }

    @Test
    void restoresCridWhileHandlingAndClearsItAfter() throws Exception {
        AtomicReference<String> duringHandle = new AtomicReference<>();
        AtomicReference<String> afterHandle = new AtomicReference<>("unset");
        AtomicInteger receives = new AtomicInteger();
        ReceiptTable table = new ReceiptTable() {
            @Override
            public void insert(ReceiptEntity entity) {
            }

            @Override
            public ReceiptEntity find(String id) {
                duringHandle.set(MDC.get(Correlation.CRID));
                throw new RuntimeException("find failed");
            }

            @Override
            public void update(ReceiptEntity entity) {
            }

            @Override
            public List<ReceiptEntity> listNewest(int limit) {
                return List.of();
            }
        };
        ReceiptJobs jobs = new ReceiptJobs() {
            private final InMemoryReceiptJobs delegate = new InMemoryReceiptJobs();

            @Override
            public void send(ReceiptJob job) {
                delegate.send(job);
            }

            @Override
            public Optional<QueuedReceipt> receive() {
                int call = receives.incrementAndGet();
                if (call == 1) {
                    return delegate.receive();
                }
                afterHandle.set(MDC.get(Correlation.CRID));
                return Optional.empty();
            }

            @Override
            public void delete(QueuedReceipt message) {
                delegate.delete(message);
            }

            @Override
            public void poison(QueuedReceipt message) {
                delegate.poison(message);
            }
        };
        jobs.send(new ReceiptJob("id-1", "crid-1", null));
        ReceiptProcessor processor = new ReceiptProcessor(
                table,
                jobs,
                new InMemoryReceiptCache(),
                new NoopReceiptTelemetry(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                false);

        Thread worker = start(new WorkerLoop(jobs, processor, OpenTelemetry.noop()));
        await(() -> receives.get() >= 2);
        stop(worker);

        assertEquals("crid-1", duringHandle.get());
        assertNull(afterHandle.get());
    }

    private static Thread start(WorkerLoop loop) {
        Thread worker = new Thread(() -> {
            try {
                loop.run(null);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });
        worker.start();
        return worker;
    }

    private static void await(BooleanSupplier ready) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("worker did not reach the expected state");
            }
            Thread.sleep(10);
        }
    }

    private static void stop(Thread worker) throws InterruptedException {
        worker.interrupt();
        worker.join(1_000);
        assertFalse(worker.isAlive());
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
}

package com.interview.receipt.worker;

import com.interview.receipt.QueuedReceipt;
import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.ReceiptStatus;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.ReceiptTelemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

public class ReceiptProcessor {
    private static final Logger log = LoggerFactory.getLogger(ReceiptProcessor.class);

    private final ReceiptTable table;
    private final ReceiptJobs jobs;
    private final ReceiptCache cache;
    private final ReceiptTelemetry telemetry;
    private final Clock clock;
    private final boolean redisEnabled;

    public ReceiptProcessor(
            ReceiptTable table,
            ReceiptJobs jobs,
            ReceiptCache cache,
            ReceiptTelemetry telemetry,
            Clock clock,
            boolean redisEnabled) {
        this.table = table;
        this.jobs = jobs;
        this.cache = cache;
        this.telemetry = telemetry;
        this.clock = clock;
        this.redisEnabled = redisEnabled;
    }

    public void handle(QueuedReceipt message) {
        String receiptId = message.job().receiptId();
        log.info("dequeued receiptId={} attempt={}", receiptId, message.dequeueCount());
        ReceiptEntity row = table.find(receiptId);
        if (row == null) {
            jobs.poison(message);
            telemetry.failed(receiptId, "missing row");
            return;
        }
        if (row.status() == ReceiptStatus.PROCESSED) {
            log.info("already processed receiptId={}", receiptId);
            jobs.delete(message);
            return;
        }
        if (message.dequeueCount() >= 5) {
            table.update(row.failed(clock.instant(), "poisoned after 5 dequeues"));
            jobs.poison(message);
            telemetry.failed(receiptId, "poisoned after 5 dequeues");
            return;
        }
        ReceiptEntity processed = row.processed(clock.instant());
        table.update(processed);
        if (redisEnabled) {
            try {
                cache.putProcessed(processed);
            } catch (RuntimeException error) {
                telemetry.cacheError(receiptId, error);
            }
        }
        jobs.delete(message);
        telemetry.processed(receiptId);
    }
}

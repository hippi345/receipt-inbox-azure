package com.interview.receipt.telemetry;

import com.interview.receipt.ReceiptTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenTelemetryReceiptTelemetry implements ReceiptTelemetry {
    private static final Logger log = LoggerFactory.getLogger(OpenTelemetryReceiptTelemetry.class);

    private final LongCounter processed;
    private final LongCounter failed;

    public OpenTelemetryReceiptTelemetry(OpenTelemetry openTelemetry) {
        var meter = openTelemetry.getMeter("receipt-inbox");
        this.processed = meter.counterBuilder("receipts.processed").build();
        this.failed = meter.counterBuilder("receipts.failed").build();
    }

    @Override
    public void processed(String receiptId) {
        processed.add(1);
        log.info("processed receiptId={}", receiptId);
    }

    @Override
    public void failed(String receiptId, String reason) {
        failed.add(1);
        log.error("failed receiptId={} reason={}", receiptId, reason);
    }

    @Override
    public void cacheError(String receiptId, RuntimeException error) {
        log.error("cache error receiptId={}", receiptId, error);
    }
}

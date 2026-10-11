package com.interview.receipt.api;

import com.interview.receipt.NoopReceiptTelemetry;
import com.interview.receipt.memory.InMemoryReceiptBlobs;
import com.interview.receipt.memory.InMemoryReceiptCache;
import com.interview.receipt.memory.InMemoryReceiptJobs;
import com.interview.receipt.memory.InMemoryReceiptTable;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class TestFakes {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneOffset.UTC);

    @Bean
    InMemoryReceiptBlobs inMemoryReceiptBlobs() {
        return new InMemoryReceiptBlobs();
    }

    @Bean
    InMemoryReceiptTable inMemoryReceiptTable() {
        return new InMemoryReceiptTable();
    }

    @Bean
    InMemoryReceiptJobs inMemoryReceiptJobs() {
        return new InMemoryReceiptJobs();
    }

    @Bean
    InMemoryReceiptCache inMemoryReceiptCache() {
        return new InMemoryReceiptCache();
    }

    @Bean
    NoopReceiptTelemetry noopReceiptTelemetry() {
        return new NoopReceiptTelemetry();
    }

    @Bean
    ReceiptIntake receiptIntake(InMemoryReceiptBlobs blobs, InMemoryReceiptTable table, InMemoryReceiptJobs jobs) {
        return new ReceiptIntake(blobs, table, jobs, CLOCK);
    }

    @Bean
    ReceiptQuery receiptQuery(InMemoryReceiptTable table, InMemoryReceiptCache cache, NoopReceiptTelemetry telemetry) {
        return new ReceiptQuery(table, cache, telemetry, true);
    }
}

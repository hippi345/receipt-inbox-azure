package com.interview.receipt.worker;

import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.ReceiptTelemetry;
import com.interview.receipt.config.ReceiptInfrastructureConfig;
import com.interview.receipt.config.ReceiptProperties;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@Profile("!test")
@Import(ReceiptInfrastructureConfig.class)
public class WorkerConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReceiptProcessor receiptProcessor(
            ReceiptTable table,
            ReceiptJobs jobs,
            ReceiptCache cache,
            ReceiptTelemetry telemetry,
            Clock clock,
            ReceiptProperties properties) {
        return new ReceiptProcessor(table, jobs, cache, telemetry, clock, properties.getRedis().isEnabled());
    }

    @Bean
    WorkerLoop workerLoop(ReceiptJobs jobs, ReceiptProcessor processor, OpenTelemetry openTelemetry) {
        return new WorkerLoop(jobs, processor, openTelemetry);
    }
}

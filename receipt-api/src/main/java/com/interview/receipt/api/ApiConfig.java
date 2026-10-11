package com.interview.receipt.api;

import com.interview.receipt.ReceiptBlobs;
import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.ReceiptTelemetry;
import com.interview.receipt.config.ReceiptInfrastructureConfig;
import com.interview.receipt.config.ReceiptProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@Profile("!test")
@Import(ReceiptInfrastructureConfig.class)
public class ApiConfig {
    @Bean
    ReceiptIntake receiptIntake(ReceiptBlobs blobs, ReceiptTable table, ReceiptJobs jobs) {
        return new ReceiptIntake(blobs, table, jobs, Clock.systemUTC());
    }

    @Bean
    ReceiptQuery receiptQuery(
            ReceiptTable table,
            ReceiptCache cache,
            ReceiptTelemetry telemetry,
            ReceiptProperties properties) {
        return new ReceiptQuery(table, cache, telemetry, properties.getRedis().isEnabled());
    }
}

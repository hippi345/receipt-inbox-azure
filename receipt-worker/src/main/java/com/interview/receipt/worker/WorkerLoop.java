package com.interview.receipt.worker;

import com.interview.receipt.QueuedReceipt;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.logging.CorrelationScope;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.Duration;
import java.util.Optional;

public class WorkerLoop implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(WorkerLoop.class);

    private final ReceiptJobs jobs;
    private final ReceiptProcessor processor;
    private final OpenTelemetry openTelemetry;

    public WorkerLoop(ReceiptJobs jobs, ReceiptProcessor processor, OpenTelemetry openTelemetry) {
        this.jobs = jobs;
        this.processor = processor;
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Optional<QueuedReceipt> message = jobs.receive();
                if (message.isPresent()) {
                    try (CorrelationScope scope = CorrelationScope.open(openTelemetry, message.get().job())) {
                        processor.handle(message.get());
                    }
                } else {
                    Thread.sleep(Duration.ofSeconds(1));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException error) {
                log.error("queue iteration failed", error);
                try {
                    Thread.sleep(Duration.ofSeconds(1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}

package com.interview.receipt.config;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertSame;

class OpenTelemetryExportTest {

    @Test
    void blankConnectionStringLeavesExportDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("applicationinsights.connection.string", "");

        OpenTelemetry telemetry = new ReceiptInfrastructureConfig().openTelemetry(environment);

        assertSame(OpenTelemetry.noop(), telemetry);
    }
}

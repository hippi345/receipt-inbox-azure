package com.interview.receipt.logging;

import com.interview.receipt.ReceiptJob;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelationScopeTest {

    @Test
    void setsContextAndClearsItWhenHandleThrows() {
        ReceiptJob job = new ReceiptJob("id-1", "crid-1", null);

        assertThrows(IllegalStateException.class, () -> {
            try (CorrelationScope scope = CorrelationScope.open(OpenTelemetry.noop(), job)) {
                assertEquals("crid-1", MDC.get(Correlation.CRID));
                assertEquals("id-1", MDC.get(Correlation.RECEIPT_ID));
                throw new IllegalStateException("fail");
            }
        });

        assertNull(MDC.get(Correlation.CRID));
        assertNull(MDC.get(Correlation.RECEIPT_ID));
    }

    @Test
    void missingCridLeavesThatKeyEmpty() {
        try (CorrelationScope scope = CorrelationScope.open(OpenTelemetry.noop(), new ReceiptJob("id-1"))) {
            assertNull(MDC.get(Correlation.CRID));
            assertEquals("id-1", MDC.get(Correlation.RECEIPT_ID));
        }
        assertNull(MDC.get(Correlation.RECEIPT_ID));
    }
}

package com.interview.receipt.api;

import com.interview.receipt.logging.Correlation;
import io.opentelemetry.api.OpenTelemetry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {

    @Test
    void assignsCridHeaderAndClearsContext() throws Exception {
        RequestCorrelationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/receipts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertNotNull(MDC.get(Correlation.CRID));

        filter.doFilter(request, response, chain);

        assertNotNull(response.getHeader(Correlation.REQUEST_HEADER));
        assertEquals(response.getHeader(Correlation.REQUEST_HEADER), request.getAttribute(Correlation.CRID));
        assertNull(MDC.get(Correlation.CRID));
        assertNull(MDC.get(Correlation.RECEIPT_ID));
    }

    @Test
    void singleGetSetsReceiptIdWhileTheCallRuns() throws Exception {
        RequestCorrelationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/receipts/id-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertEquals("id-1", MDC.get(Correlation.RECEIPT_ID));

        filter.doFilter(request, response, chain);

        assertNotNull(response.getHeader(Correlation.REQUEST_HEADER));
        assertNull(MDC.get(Correlation.RECEIPT_ID));
    }

    @Test
    void listGetDoesNotSetReceiptId() throws Exception {
        RequestCorrelationFilter filter = filter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/receipts");
        FilterChain chain = (req, res) -> assertNull(MDC.get(Correlation.RECEIPT_ID));

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    private static RequestCorrelationFilter filter() {
        return new RequestCorrelationFilter(OpenTelemetry.noop());
    }
}

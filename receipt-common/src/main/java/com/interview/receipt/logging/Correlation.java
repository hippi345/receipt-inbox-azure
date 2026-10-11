package com.interview.receipt.logging;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public final class Correlation {
    public static final String CRID = "crid";
    public static final String RECEIPT_ID = "receiptId";
    public static final String REQUEST_HEADER = "X-Request-Id";

    private Correlation() {
    }

    public static void setCrid(String crid) {
        MDC.put(CRID, crid);
    }

    public static void setReceiptId(String receiptId) {
        MDC.put(RECEIPT_ID, receiptId);
    }

    public static String crid() {
        return MDC.get(CRID);
    }

    public static com.azure.core.util.Context azureContext() {
        String crid = crid();
        if (crid == null || crid.isBlank()) {
            return com.azure.core.util.Context.NONE;
        }
        com.azure.core.http.HttpHeaders headers = new com.azure.core.http.HttpHeaders();
        headers.set("x-ms-client-request-id", crid);
        return new com.azure.core.util.Context(
                com.azure.core.http.policy.AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY,
                headers);
    }

    public static void clear() {
        MDC.remove(CRID);
        MDC.remove(RECEIPT_ID);
    }

    public static String currentTraceparent() {
        if (!Span.current().getSpanContext().isValid()) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        TextMapSetter<Map<String, String>> setter = Map::put;
        W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, setter);
        return carrier.get("traceparent");
    }
}

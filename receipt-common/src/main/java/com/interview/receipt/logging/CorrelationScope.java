package com.interview.receipt.logging;

import com.interview.receipt.ReceiptJob;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Collections;

public final class CorrelationScope implements AutoCloseable {
    private final Span span;
    private final Scope scope;

    private CorrelationScope(Span span, Scope scope) {
        this.span = span;
        this.scope = scope;
    }

    public static CorrelationScope open(OpenTelemetry openTelemetry, ReceiptJob job) {
        if (job.crid() != null) {
            Correlation.setCrid(job.crid());
        }
        Correlation.setReceiptId(job.receiptId());
        if (job.traceparent() == null) {
            return new CorrelationScope(null, null);
        }
        TextMapGetter<String> getter = new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(String carrier) {
                return Collections.singleton("traceparent");
            }

            @Override
            public String get(String carrier, String key) {
                return "traceparent".equals(key) ? carrier : null;
            }
        };
        Context parent = W3CTraceContextPropagator.getInstance()
                .extract(Context.root(), job.traceparent(), getter);
        Span span = openTelemetry.getTracer("receipt-inbox")
                .spanBuilder("process receipt")
                .setParent(parent)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();
        return new CorrelationScope(span, span.makeCurrent());
    }

    @Override
    public void close() {
        try {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        } finally {
            Correlation.clear();
        }
    }
}

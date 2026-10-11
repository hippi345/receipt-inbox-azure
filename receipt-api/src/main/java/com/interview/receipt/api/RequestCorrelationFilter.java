package com.interview.receipt.api;

import com.interview.receipt.logging.Correlation;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.UUID;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private final OpenTelemetry openTelemetry;

    @Autowired
    public RequestCorrelationFilter(ObjectProvider<OpenTelemetry> openTelemetry) {
        this(openTelemetry.getIfAvailable(OpenTelemetry::noop));
    }

    RequestCorrelationFilter(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws java.io.IOException, jakarta.servlet.ServletException {
        String crid = UUID.randomUUID().toString();
        response.setHeader(Correlation.REQUEST_HEADER, crid);
        request.setAttribute(Correlation.CRID, crid);
        String receiptId = receiptId(request);
        Span span = openTelemetry.getTracer("receipt-inbox")
                .spanBuilder(request.getMethod() + " " + request.getRequestURI())
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Correlation.setCrid(crid);
            if (receiptId != null) {
                Correlation.setReceiptId(receiptId);
            }
            chain.doFilter(request, response);
            if (response.getStatus() >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
        } finally {
            Correlation.clear();
            span.end();
        }
    }

    private static String receiptId(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        String prefix = "/receipts/";
        if (path == null || !path.startsWith(prefix)) {
            return null;
        }
        String id = path.substring(prefix.length());
        if (id.isEmpty() || id.indexOf('/') >= 0) {
            return null;
        }
        return id;
    }
}

package com.interview.receipt.logging;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.AddHeadersFromContextPolicy;
import com.azure.core.http.policy.RequestIdPolicy;
import com.azure.core.util.Context;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationAzureContextTest {

    @Test
    void usesTheCridAsTheClientRequestId() {
        AtomicReference<String> recordedClientRequestId = new AtomicReference<>();
        HttpClient httpClient = request -> {
            recordedClientRequestId.set(request.getHeaders().getValue("x-ms-client-request-id"));
            return Mono.just(successResponse(request));
        };
        HttpPipeline pipeline = new HttpPipelineBuilder()
                .httpClient(httpClient)
                .policies(new RequestIdPolicy(), new AddHeadersFromContextPolicy())
                .build();

        try {
            Correlation.setCrid("crid-1");

            Context azureContext = Correlation.azureContext();
            assertTrue(azureContext.getData(AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY).isPresent());
            Object headersValue = azureContext.getData(AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY).get();
            assertInstanceOf(HttpHeaders.class, headersValue);
            assertEquals("crid-1", ((HttpHeaders) headersValue).getValue("x-ms-client-request-id"));

            pipeline.sendSync(
                    new HttpRequest(HttpMethod.PUT, "https://example.blob.core.windows.net/receipts/a"),
                    Correlation.azureContext());
            assertEquals("crid-1", recordedClientRequestId.get());

            Correlation.clear();
            pipeline.sendSync(
                    new HttpRequest(HttpMethod.PUT, "https://example.blob.core.windows.net/receipts/a"),
                    Correlation.azureContext());
            assertNotEquals("crid-1", recordedClientRequestId.get());
            assertFalse(Correlation.azureContext()
                    .getData(AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY)
                    .isPresent());

            Correlation.setCrid(" ");
            assertFalse(Correlation.azureContext()
                    .getData(AddHeadersFromContextPolicy.AZURE_REQUEST_HTTP_HEADERS_KEY)
                    .isPresent());
        } finally {
            Correlation.clear();
        }
    }

    private static HttpResponse successResponse(HttpRequest request) {
        return new HttpResponse(request) {
            @Override
            public int getStatusCode() {
                return 200;
            }

            @Override
            public String getHeaderValue(String name) {
                return null;
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }

            @Override
            public Flux<ByteBuffer> getBody() {
                return Flux.empty();
            }

            @Override
            public Mono<byte[]> getBodyAsByteArray() {
                return Mono.just(new byte[0]);
            }

            @Override
            public Mono<String> getBodyAsString() {
                return Mono.just("");
            }

            @Override
            public Mono<String> getBodyAsString(Charset charset) {
                return Mono.just("");
            }
        };
    }
}

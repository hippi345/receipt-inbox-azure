package com.interview.receipt.config;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpPipelinePosition;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.queue.QueueClient;
import com.interview.receipt.logging.Correlation;
import com.interview.receipt.logging.CorrelationHeaderPolicy;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductionStorageClientCorrelationTest {
    private static final String CONNECTION = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
            + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            + "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;"
            + "QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;"
            + "TableEndpoint=http://127.0.0.1:10002/devstoreaccount1;";

    private static final byte[] QUEUE_SENT = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<QueueMessagesList><QueueMessage>"
                    + "<MessageId>m1</MessageId>"
                    + "<InsertionTime>Fri, 09 Oct 2026 00:00:00 GMT</InsertionTime>"
                    + "<ExpirationTime>Fri, 16 Oct 2026 00:00:00 GMT</ExpirationTime>"
                    + "<PopReceipt>p1</PopReceipt>"
                    + "<TimeNextVisible>Fri, 09 Oct 2026 00:00:00 GMT</TimeNextVisible>"
                    + "</QueueMessage></QueueMessagesList>").getBytes(StandardCharsets.UTF_8);

    private static final byte[] QUEUE_EMPTY = (
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><QueueMessagesList></QueueMessagesList>")
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void correlationHeaderIsAppliedBeforeTheStorageSignature() {
        assertEquals(HttpPipelinePosition.PER_CALL, new CorrelationHeaderPolicy().getPipelinePosition());
    }

    @Test
    void productionClientsSendTheCridAndReceiveKeepsARandomId() {
        List<String> clientRequestIds = Collections.synchronizedList(new ArrayList<>());
        HttpClient httpClient = request -> {
            clientRequestIds.add(request.getHeaders().getValue(HttpHeaderName.X_MS_CLIENT_REQUEST_ID));
            return Mono.just(success(request));
        };
        BlobClient blob = ReceiptInfrastructureConfig.blobServiceClientBuilder(CONNECTION)
                .httpClient(httpClient)
                .buildClient()
                .getBlobContainerClient("receipts")
                .getBlobClient("a");
        QueueClient queue = ReceiptInfrastructureConfig.queueServiceClientBuilder(CONNECTION)
                .httpClient(httpClient)
                .buildClient()
                .getQueueClient("receipt-jobs");
        TableClient table = ReceiptInfrastructureConfig.tableServiceClientBuilder(CONNECTION)
                .httpClient(httpClient)
                .buildClient()
                .getTableClient("receipts");

        try {
            Correlation.setCrid("crid-1");

            clientRequestIds.clear();
            blob.uploadWithResponse(
                    new BlobParallelUploadOptions(BinaryData.fromBytes(new byte[] {1})),
                    null,
                    Correlation.azureContext());
            assertEquals(List.of("crid-1"), List.copyOf(clientRequestIds));

            clientRequestIds.clear();
            queue.sendMessageWithResponse("job", null, null, null, Correlation.azureContext());
            assertEquals(List.of("crid-1"), List.copyOf(clientRequestIds));

            clientRequestIds.clear();
            table.createEntityWithResponse(new TableEntity("receipts", "id-1"), null, Correlation.azureContext());
            assertEquals(List.of("crid-1"), List.copyOf(clientRequestIds));

            clientRequestIds.clear();
            assertFalse(queue.receiveMessages(1, Duration.ofMinutes(1), null, Context.NONE).iterator().hasNext());
            assertFalse(clientRequestIds.isEmpty());
            for (String clientRequestId : clientRequestIds) {
                assertNotNull(clientRequestId);
                assertNotEquals("crid-1", clientRequestId);
            }
        } finally {
            Correlation.clear();
        }
    }

    private static HttpResponse success(HttpRequest request) {
        byte[] body = body(request);
        int status = status(request);
        HttpHeaders headers = new HttpHeaders();
        if (body.length > 0) {
            headers.set(HttpHeaderName.CONTENT_TYPE, "application/xml");
        }
        return new HttpResponse(request) {
            @Override
            public int getStatusCode() {
                return status;
            }

            @Override
            public String getHeaderValue(String name) {
                return headers.getValue(name);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }

            @Override
            public Flux<ByteBuffer> getBody() {
                if (body.length == 0) {
                    return Flux.empty();
                }
                return Flux.defer(() -> Flux.just(ByteBuffer.wrap(body.clone())));
            }

            @Override
            public Mono<byte[]> getBodyAsByteArray() {
                return Mono.just(body);
            }

            @Override
            public Mono<String> getBodyAsString() {
                return Mono.just(new String(body, StandardCharsets.UTF_8));
            }

            @Override
            public Mono<String> getBodyAsString(Charset charset) {
                return Mono.just(new String(body, charset));
            }
        };
    }

    private static int status(HttpRequest request) {
        String url = request.getUrl().toString();
        if (url.contains(":10002/")) {
            return 204;
        }
        if (url.contains(":10001/") && request.getHttpMethod() != HttpMethod.POST) {
            return 200;
        }
        return 201;
    }

    private static byte[] body(HttpRequest request) {
        String url = request.getUrl().toString();
        if (!url.contains(":10001/")) {
            return new byte[0];
        }
        if (request.getHttpMethod() == HttpMethod.POST) {
            return QUEUE_SENT;
        }
        return QUEUE_EMPTY;
    }
}

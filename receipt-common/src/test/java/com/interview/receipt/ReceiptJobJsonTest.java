package com.interview.receipt;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReceiptJobJsonTest {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void roundTripsCridAndTraceparent() throws Exception {
        ReceiptJob job = new ReceiptJob("id-1", "crid-1", "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01");

        ReceiptJob read = json.readValue(json.writeValueAsString(job), ReceiptJob.class);

        assertEquals("id-1", read.receiptId());
        assertEquals("crid-1", read.crid());
        assertEquals("00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-bbbbbbbbbbbbbbbb-01", read.traceparent());
    }

    @Test
    void messageWithOnlyReceiptIdDeserializes() throws Exception {
        ReceiptJob read = json.readValue("{\"receiptId\":\"id-1\"}", ReceiptJob.class);

        assertEquals("id-1", read.receiptId());
        assertNull(read.crid());
        assertNull(read.traceparent());
    }
}

package com.interview.receipt.api;

import com.interview.receipt.logging.Correlation;
import com.interview.receipt.memory.InMemoryReceiptJobs;
import com.interview.receipt.memory.InMemoryReceiptTable;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestFakes.class)
class ReceiptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryReceiptTable table;

    @Autowired
    private InMemoryReceiptJobs jobs;

    @Test
    void uploadReturnsAcceptedReceiptThatCanBeRead() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "lunch.pdf", "application/pdf", new byte[]{1, 2});

        MvcResult uploaded = mockMvc.perform(multipart("/receipts")
                        .file(file)
                        .param("merchant", "Cafe")
                        .param("amount", "9.00")
                        .param("notes", "lunch"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(Correlation.REQUEST_HEADER, not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.fileName").value("lunch.pdf"))
                .andExpect(jsonPath("$.merchant").value("Cafe"))
                .andExpect(jsonPath("$.amount").value("9.00"))
                .andExpect(jsonPath("$.notes").value("lunch"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-08T15:00:00Z"))
                .andReturn();
        String uploadCrid = uploaded.getResponse().getHeader(Correlation.REQUEST_HEADER);
        assertEquals(uploadCrid, jobs.receive().orElseThrow().job().crid());
        String id = JsonPath.read(uploaded.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

        String getCrid = mockMvc.perform(get("/receipts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string(Correlation.REQUEST_HEADER, not(blankOrNullString())))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("received"))
                .andExpect(jsonPath("$.fileName").value("lunch.pdf"))
                .andExpect(jsonPath("$.merchant").value("Cafe"))
                .andExpect(jsonPath("$.amount").value("9.00"))
                .andExpect(jsonPath("$.notes").value("lunch"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-08T15:00:00Z"))
                .andReturn()
                .getResponse()
                .getHeader(Correlation.REQUEST_HEADER);

        String listCrid = mockMvc.perform(get("/receipts"))
                .andExpect(status().isOk())
                .andExpect(header().string(Correlation.REQUEST_HEADER, not(blankOrNullString())))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id", hasItem(id)))
                .andReturn()
                .getResponse()
                .getHeader(Correlation.REQUEST_HEADER);

        assertNotEquals(uploadCrid, getCrid);
        assertNotEquals(uploadCrid, listCrid);
        assertNotEquals(getCrid, listCrid);
    }

    @Test
    void missingReceiptReturnsNotFound() throws Exception {
        mockMvc.perform(get("/receipts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(Correlation.REQUEST_HEADER, not(blankOrNullString())))
                .andExpect(content().json("{\"message\":\"receipt not found\"}", JsonCompareMode.STRICT));
    }

    @Test
    void uploadWithoutFileReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/receipts")
                        .param("merchant", "Cafe")
                        .param("amount", "9.00")
                        .param("notes", "lunch"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"file is required\"}", JsonCompareMode.STRICT));
    }

    @Test
    void invalidFileNameReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/receipts")
                        .file(new MockMultipartFile("file", "a/b.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"file is required\"}", JsonCompareMode.STRICT));
    }

    @Test
    void storageFailureReturnsTheExceptionMessage() throws Exception {
        table.failNextInsert();

        mockMvc.perform(multipart("/receipts")
                        .file(new MockMultipartFile("file", "lunch.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"message\":\"table write failed\"}", JsonCompareMode.STRICT));
    }

    @Test
    void queueFailureReturnsTheExceptionMessage() throws Exception {
        jobs.failNextSend();

        mockMvc.perform(multipart("/receipts")
                        .file(new MockMultipartFile("file", "lunch.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"message\":\"queue send failed\"}", JsonCompareMode.STRICT));
    }
}

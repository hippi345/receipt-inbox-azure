package com.interview.receipt.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {
    private final ReceiptIntake intake;
    private final ReceiptQuery query;

    public ReceiptController(ReceiptIntake intake, ReceiptQuery query) {
        this.intake = intake;
        this.query = query;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "merchant", required = false) String merchant,
            @RequestParam(value = "amount", required = false) String amount,
            @RequestParam(value = "notes", required = false) String notes) throws IOException {
        ReceiptResponse body = ReceiptResponse.from(intake.submit(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                merchant,
                amount,
                notes));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{id}")
    public ReceiptResponse get(@PathVariable String id) {
        return ReceiptResponse.from(query.get(id));
    }

    @GetMapping
    public List<ReceiptResponse> list() {
        return query.list().stream().map(ReceiptResponse::from).toList();
    }
}

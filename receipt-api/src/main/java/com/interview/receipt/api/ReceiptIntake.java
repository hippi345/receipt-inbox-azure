package com.interview.receipt.api;

import com.interview.receipt.logging.Correlation;
import com.interview.receipt.ReceiptBlobs;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptJob;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.ReceiptStatus;
import com.interview.receipt.ReceiptTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.UUID;

public class ReceiptIntake {
    private static final Logger log = LoggerFactory.getLogger(ReceiptIntake.class);

    private final ReceiptBlobs blobs;
    private final ReceiptTable table;
    private final ReceiptJobs jobs;
    private final Clock clock;

    public ReceiptIntake(ReceiptBlobs blobs, ReceiptTable table, ReceiptJobs jobs, Clock clock) {
        this.blobs = blobs;
        this.table = table;
        this.jobs = jobs;
        this.clock = clock;
    }

    public ReceiptEntity submit(String fileName, String contentType, byte[] body, String merchant, String amount, String notes) {
        if (fileName == null || fileName.isBlank() || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("file name is invalid");
        }

        String id = UUID.randomUUID().toString();
        Correlation.setReceiptId(id);
        String blobPath = "receipts/" + id + "/" + fileName;
        log.info("submit receiptId={} file={} merchant={}", id, fileName, merchant);
        try {
            blobs.upload(blobPath, body, contentType);
        } catch (RuntimeException error) {
            throw new ReceiptStorageException("blob upload failed", error);
        }

        ReceiptEntity entity = new ReceiptEntity(
                id,
                ReceiptStatus.RECEIVED,
                fileName,
                blobPath,
                merchant,
                amount,
                notes,
                clock.instant(),
                null,
                null);
        try {
            table.insert(entity);
        } catch (RuntimeException error) {
            try {
                blobs.delete(blobPath);
            } catch (RuntimeException deleteError) {
                log.warn("blob delete failed after table write failed", deleteError);
            }
            throw new ReceiptStorageException("table write failed", error);
        }

        try {
            jobs.send(new ReceiptJob(id, Correlation.crid(), Correlation.currentTraceparent()));
        } catch (RuntimeException error) {
            table.update(entity.failed(clock.instant(), "queue send failed"));
            throw new ReceiptStorageException("queue send failed", error);
        }
        log.info("accepted receiptId={}", id);
        return entity;
    }
}

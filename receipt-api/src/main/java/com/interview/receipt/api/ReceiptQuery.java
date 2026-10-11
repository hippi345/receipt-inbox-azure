package com.interview.receipt.api;

import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.ReceiptTelemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ReceiptQuery {
    private static final Logger log = LoggerFactory.getLogger(ReceiptQuery.class);

    private final ReceiptTable table;
    private final ReceiptCache cache;
    private final ReceiptTelemetry telemetry;
    private final boolean redisEnabled;

    public ReceiptQuery(ReceiptTable table, ReceiptCache cache, ReceiptTelemetry telemetry, boolean redisEnabled) {
        this.table = table;
        this.cache = cache;
        this.telemetry = telemetry;
        this.redisEnabled = redisEnabled;
    }

    public ReceiptEntity get(String id) {
        if (redisEnabled) {
            try {
                var hit = cache.find(id);
                if (hit.isPresent()) {
                    log.info("get receiptId={} from cache", id);
                    return hit.get();
                }
            } catch (RuntimeException error) {
                telemetry.cacheError(id, error);
            }
        }
        ReceiptEntity row = table.find(id);
        if (row == null) {
            log.info("get receiptId={} missing", id);
            throw new ReceiptNotFoundException("receipt not found", null);
        }
        log.info("get receiptId={} from table", id);
        return row;
    }

    public List<ReceiptEntity> list() {
        if (redisEnabled) {
            try {
                var hit = cache.recent();
                if (hit.isPresent()) {
                    log.info("list count={} from cache", hit.get().size());
                    return hit.get();
                }
            } catch (RuntimeException error) {
                telemetry.cacheError("recent", error);
            }
        }
        List<ReceiptEntity> rows = table.listNewest(20);
        log.info("list count={} from table", rows.size());
        return rows;
    }
}

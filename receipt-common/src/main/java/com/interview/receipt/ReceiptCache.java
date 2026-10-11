package com.interview.receipt;

import java.util.List;
import java.util.Optional;

public interface ReceiptCache {
    Optional<ReceiptEntity> find(String id);
    Optional<List<ReceiptEntity>> recent();
    void putProcessed(ReceiptEntity entity);
}

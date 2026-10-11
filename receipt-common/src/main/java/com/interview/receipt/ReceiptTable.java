package com.interview.receipt;

import java.util.List;

public interface ReceiptTable {
    void insert(ReceiptEntity entity);
    ReceiptEntity find(String id);
    void update(ReceiptEntity entity);
    List<ReceiptEntity> listNewest(int limit);
}

package com.interview.receipt.memory;

import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptTable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryReceiptTable implements ReceiptTable {
    private final Map<String, ReceiptEntity> rows = new LinkedHashMap<>();
    private int readCount;
    private boolean failNextInsert;

    @Override
    public void insert(ReceiptEntity entity) {
        if (failNextInsert) {
            failNextInsert = false;
            throw new IllegalStateException();
        }
        rows.put(entity.id(), entity);
    }

    @Override
    public ReceiptEntity find(String id) {
        readCount++;
        return rows.get(id);
    }

    @Override
    public void update(ReceiptEntity entity) {
        rows.put(entity.id(), entity);
    }

    @Override
    public List<ReceiptEntity> listNewest(int limit) {
        readCount++;
        return rows.values().stream()
                .sorted(Comparator.comparing(ReceiptEntity::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    public int readCount() {
        return readCount;
    }

    public void failNextInsert() {
        failNextInsert = true;
    }

    public int size() {
        return rows.size();
    }
}

package com.interview.receipt.memory;

import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryReceiptCache implements ReceiptCache {
    private static final int RECENT_LIMIT = 20;

    private final Map<String, ReceiptEntity> entries = new HashMap<>();
    private final List<ReceiptEntity> recent = new ArrayList<>();
    private boolean recentReady;
    private boolean failNext;

    @Override
    public Optional<ReceiptEntity> find(String id) {
        failIfRequested();
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public Optional<List<ReceiptEntity>> recent() {
        failIfRequested();
        if (!recentReady) {
            return Optional.empty();
        }
        return Optional.of(List.copyOf(recent));
    }

    @Override
    public void putProcessed(ReceiptEntity entity) {
        failIfRequested();
        entries.put(entity.id(), entity);
        recent.removeIf(existing -> existing.id().equals(entity.id()));
        recent.add(0, entity);
        if (recent.size() > RECENT_LIMIT) {
            recent.subList(RECENT_LIMIT, recent.size()).clear();
        }
        recentReady = true;
    }

    public void failNext() {
        failNext = true;
    }

    private void failIfRequested() {
        if (failNext) {
            failNext = false;
            throw new IllegalStateException("redis down");
        }
    }
}

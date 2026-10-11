package com.interview.receipt.redis;

import com.interview.receipt.ReceiptCache;
import com.interview.receipt.ReceiptEntity;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RedisReceiptCache implements ReceiptCache {
    static final Duration TTL = Duration.ofDays(3);
    static final int RECENT_LIMIT = 20;
    static final String RECENT_KEY = "receipts:recent";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RedisReceiptCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public Optional<ReceiptEntity> find(String id) {
        String value = redis.opsForValue().get(key(id));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(json.readValue(value, ReceiptEntity.class));
    }

    @Override
    public Optional<List<ReceiptEntity>> recent() {
        String value = redis.opsForValue().get(RECENT_KEY);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(json.readValue(value, new TypeReference<List<ReceiptEntity>>() {
        }));
    }

    @Override
    public void putProcessed(ReceiptEntity entity) {
        redis.opsForValue().set(key(entity.id()), json.writeValueAsString(entity), TTL);
        List<ReceiptEntity> recent = new ArrayList<>(recent().orElseGet(List::of));
        recent.removeIf(existing -> existing.id().equals(entity.id()));
        recent.add(0, entity);
        if (recent.size() > RECENT_LIMIT) {
            recent = new ArrayList<>(recent.subList(0, RECENT_LIMIT));
        }
        redis.opsForValue().set(RECENT_KEY, json.writeValueAsString(recent), TTL);
    }

    static String key(String id) {
        return "receipt:" + id;
    }
}

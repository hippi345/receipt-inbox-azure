package com.interview.receipt.memory;

import com.interview.receipt.ReceiptBlobs;

import java.util.HashMap;
import java.util.Map;

public class InMemoryReceiptBlobs implements ReceiptBlobs {
    private final Map<String, byte[]> objects = new HashMap<>();
    private boolean failNextDelete;

    @Override
    public void upload(String blobPath, byte[] content, String contentType) {
        objects.put(blobPath, content);
    }

    @Override
    public void delete(String blobPath) {
        if (failNextDelete) {
            failNextDelete = false;
            throw new IllegalStateException();
        }
        objects.remove(blobPath);
    }

    public void failNextDelete() {
        failNextDelete = true;
    }

    public boolean contains(String blobPath) {
        return objects.containsKey(blobPath);
    }

    public boolean containsAny() {
        return !objects.isEmpty();
    }
}

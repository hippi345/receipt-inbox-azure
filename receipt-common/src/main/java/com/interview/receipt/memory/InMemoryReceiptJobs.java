package com.interview.receipt.memory;

import com.interview.receipt.QueuedReceipt;
import com.interview.receipt.ReceiptJob;
import com.interview.receipt.ReceiptJobs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class InMemoryReceiptJobs implements ReceiptJobs {
    private final ArrayDeque<QueuedReceipt> queue = new ArrayDeque<>();
    private final List<QueuedReceipt> poisoned = new ArrayList<>();
    private boolean failNextSend;

    @Override
    public void send(ReceiptJob job) {
        if (failNextSend) {
            failNextSend = false;
            throw new IllegalStateException();
        }
        queue.addLast(new QueuedReceipt(newId(), newId(), 0, job));
    }

    @Override
    public Optional<QueuedReceipt> receive() {
        QueuedReceipt stored = queue.poll();
        if (stored == null) {
            return Optional.empty();
        }
        return Optional.of(new QueuedReceipt(
                stored.messageId(), stored.popReceipt(), stored.dequeueCount() + 1, stored.job()));
    }

    @Override
    public void delete(QueuedReceipt message) {
        queue.removeIf(stored -> stored.messageId().equals(message.messageId()));
    }

    @Override
    public void poison(QueuedReceipt message) {
        queue.removeIf(stored -> stored.messageId().equals(message.messageId()));
        poisoned.add(message);
    }

    public void release(QueuedReceipt message) {
        queue.addFirst(message);
    }

    public void failNextSend() {
        failNextSend = true;
    }

    public List<QueuedReceipt> poisoned() {
        return List.copyOf(poisoned);
    }

    public int queuedCount() {
        return queue.size();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}

package com.interview.receipt;

public record QueuedReceipt(String messageId, String popReceipt, int dequeueCount, ReceiptJob job) {}

package com.interview.receipt;

import java.util.Optional;

public interface ReceiptJobs {
    void send(ReceiptJob job);
    Optional<QueuedReceipt> receive();
    void delete(QueuedReceipt message);
    void poison(QueuedReceipt message);
}

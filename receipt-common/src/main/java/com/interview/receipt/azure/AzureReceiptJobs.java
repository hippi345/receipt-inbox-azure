package com.interview.receipt.azure;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.interview.receipt.QueuedReceipt;
import com.interview.receipt.ReceiptJob;
import com.interview.receipt.ReceiptJobs;
import com.interview.receipt.logging.Correlation;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Iterator;
import java.util.Optional;

public class AzureReceiptJobs implements ReceiptJobs {
    private final QueueClient jobs;
    private final QueueClient poison;
    private final ObjectMapper json;

    public AzureReceiptJobs(QueueClient jobs, QueueClient poison, ObjectMapper json) {
        this.jobs = jobs;
        this.poison = poison;
        this.json = json;
    }

    @Override
    public void send(ReceiptJob job) {
        jobs.sendMessageWithResponse(body(job), null, null, null, Correlation.azureContext());
    }

    @Override
    public Optional<QueuedReceipt> receive() {
        Iterator<QueueMessageItem> messages = jobs
                .receiveMessages(1, Duration.ofMinutes(1), null, Context.NONE)
                .iterator();
        if (!messages.hasNext()) {
            return Optional.empty();
        }
        QueueMessageItem message = messages.next();
        String body = message.getBody().toString();
        try {
            ReceiptJob job = json.readValue(body, ReceiptJob.class);
            return Optional.of(new QueuedReceipt(
                    message.getMessageId(),
                    message.getPopReceipt(),
                    (int) message.getDequeueCount(),
                    job));
        } catch (RuntimeException parseError) {
            poison.sendMessageWithResponse(body, null, null, null, Correlation.azureContext());
            jobs.deleteMessageWithResponse(
                    message.getMessageId(), message.getPopReceipt(), null, Correlation.azureContext());
            return Optional.empty();
        }
    }

    @Override
    public void delete(QueuedReceipt message) {
        jobs.deleteMessageWithResponse(message.messageId(), message.popReceipt(), null, Correlation.azureContext());
    }

    @Override
    public void poison(QueuedReceipt message) {
        poison.sendMessageWithResponse(body(message.job()), null, null, null, Correlation.azureContext());
        jobs.deleteMessageWithResponse(message.messageId(), message.popReceipt(), null, Correlation.azureContext());
    }

    private String body(ReceiptJob job) {
        return json.writeValueAsString(job);
    }
}

package com.interview.receipt.azure;

import com.azure.data.tables.models.TableEntity;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptStatus;

import java.time.Instant;

public final class ReceiptTableMapper {
    public static final String PARTITION_KEY = "receipt";

    private ReceiptTableMapper() {
    }

    public static TableEntity toTable(ReceiptEntity entity) {
        TableEntity table = new TableEntity(PARTITION_KEY, entity.id());
        table.addProperty("status", entity.status().value());
        table.addProperty("fileName", entity.fileName());
        table.addProperty("blobPath", entity.blobPath());
        addIfPresent(table, "merchant", entity.merchant());
        addIfPresent(table, "amount", entity.amount());
        addIfPresent(table, "notes", entity.notes());
        table.addProperty("createdAt", entity.createdAt().toString());
        if (entity.processedAt() != null) {
            table.addProperty("processedAt", entity.processedAt().toString());
        }
        addIfPresent(table, "failureReason", entity.failureReason());
        return table;
    }

    public static ReceiptEntity fromTable(TableEntity table) {
        return new ReceiptEntity(
                table.getRowKey(),
                ReceiptStatus.fromValue(text(table, "status")),
                text(table, "fileName"),
                text(table, "blobPath"),
                text(table, "merchant"),
                text(table, "amount"),
                text(table, "notes"),
                Instant.parse(text(table, "createdAt")),
                instant(table, "processedAt"),
                text(table, "failureReason"));
    }

    private static void addIfPresent(TableEntity table, String name, String value) {
        if (value != null) {
            table.addProperty(name, value);
        }
    }

    private static String text(TableEntity table, String name) {
        Object value = table.getProperty(name);
        return value == null ? null : value.toString();
    }

    private static Instant instant(TableEntity table, String name) {
        String value = text(table, name);
        return value == null ? null : Instant.parse(value);
    }
}

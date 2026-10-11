package com.interview.receipt.azure;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.data.tables.models.TableServiceException;
import com.interview.receipt.ReceiptEntity;
import com.interview.receipt.ReceiptTable;
import com.interview.receipt.logging.Correlation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AzureReceiptTable implements ReceiptTable {
    private final TableClient table;

    public AzureReceiptTable(TableClient table) {
        this.table = table;
    }

    @Override
    public void insert(ReceiptEntity entity) {
        table.createEntityWithResponse(ReceiptTableMapper.toTable(entity), null, Correlation.azureContext());
    }

    @Override
    public ReceiptEntity find(String id) {
        try {
            return ReceiptTableMapper.fromTable(table.getEntityWithResponse(
                    ReceiptTableMapper.PARTITION_KEY, id, null, null, Correlation.azureContext()).getValue());
        } catch (TableServiceException exception) {
            if (exception.getResponse() != null && exception.getResponse().getStatusCode() == 404) {
                return null;
            }
            throw exception;
        }
    }

    @Override
    public void update(ReceiptEntity entity) {
        table.updateEntityWithResponse(
                ReceiptTableMapper.toTable(entity),
                TableEntityUpdateMode.REPLACE,
                false,
                null,
                Correlation.azureContext());
    }

    @Override
    public List<ReceiptEntity> listNewest(int limit) {
        ListEntitiesOptions options = new ListEntitiesOptions()
                .setFilter("PartitionKey eq '" + ReceiptTableMapper.PARTITION_KEY + "'");
        List<ReceiptEntity> rows = new ArrayList<>();
        for (TableEntity entity : table.listEntities(options, null, Correlation.azureContext())) {
            rows.add(ReceiptTableMapper.fromTable(entity));
        }
        rows.sort(Comparator.comparing(ReceiptEntity::createdAt).reversed());
        if (rows.size() <= limit) {
            return List.copyOf(rows);
        }
        return List.copyOf(rows.subList(0, limit));
    }
}

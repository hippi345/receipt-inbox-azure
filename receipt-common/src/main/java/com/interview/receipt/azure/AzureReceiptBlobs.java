package com.interview.receipt.azure;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.interview.receipt.ReceiptBlobs;
import com.interview.receipt.logging.Correlation;

public class AzureReceiptBlobs implements ReceiptBlobs {
    private final BlobContainerClient container;

    public AzureReceiptBlobs(BlobContainerClient container) {
        this.container = container;
    }

    @Override
    public void upload(String blobPath, byte[] content, String contentType) {
        BlobClient blob = container.getBlobClient(blobPath);
        BlobHttpHeaders headers = contentType == null ? null : new BlobHttpHeaders().setContentType(contentType);
        blob.uploadWithResponse(
                new BlobParallelUploadOptions(BinaryData.fromBytes(content)).setHeaders(headers),
                null,
                Correlation.azureContext());
    }

    @Override
    public void delete(String blobPath) {
        container.getBlobClient(blobPath).deleteIfExistsWithResponse(null, null, null, Correlation.azureContext());
    }
}

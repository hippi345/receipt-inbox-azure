package com.interview.receipt;

public interface ReceiptBlobs {
    void upload(String blobPath, byte[] content, String contentType);
    void delete(String blobPath);
}

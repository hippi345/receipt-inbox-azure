package com.interview.receipt.api;

public class ReceiptNotFoundException extends RuntimeException {
    public ReceiptNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

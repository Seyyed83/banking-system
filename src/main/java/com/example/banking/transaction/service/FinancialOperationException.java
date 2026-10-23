package com.example.banking.transaction.service;

public class FinancialOperationException extends RuntimeException {

    private final String failureCode;

    public FinancialOperationException(String failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public String getFailureCode() {
        return failureCode;
    }
}

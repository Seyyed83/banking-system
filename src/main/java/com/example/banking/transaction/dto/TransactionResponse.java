package com.example.banking.transaction.dto;

import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TransactionResponse(
        String requestCode,
        TransactionType type,
        Long sourceAccountId,
        Long destinationAccountId,
        BigDecimal amount,
        TransactionRequestStatus status,
        int attemptCount,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt,
        String failureCode,
        String failureMessage,
        List<FinancialTransactionResponse> financialTransactions) {

    public static TransactionResponse from(
            TransactionRequest request,
            List<FinancialTransactionResponse> financialTransactions) {
        return new TransactionResponse(
                request.getRequestCode(),
                request.getTransactionType(),
                request.getSourceAccountId(),
                request.getDestinationAccountId(),
                request.getAmount(),
                request.getStatus(),
                request.getAttemptCount(),
                request.getCreatedAt(),
                request.getProcessedAt(),
                request.getFailureCode(),
                resolveFailureMessage(request.getFailureCode()),
                List.copyOf(financialTransactions));
    }

    public static String resolveFailureMessage(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return null;
        }
        return switch (failureCode) {
            case "INSUFFICIENT_FUNDS" ->
                    "Insufficient account balance to cover the transaction amount and applicable fee.";
            case "ACCOUNT_NOT_ACTIVE", "SOURCE_ACCOUNT_NOT_ACTIVE" ->
                    "The account is inactive or blocked and cannot process financial transactions.";
            case "DESTINATION_ACCOUNT_NOT_ACTIVE" ->
                    "The destination account is inactive or blocked and cannot receive funds.";
            case "ACCOUNT_NOT_FOUND" ->
                    "One or more specified accounts could not be found.";
            case "INVALID_ACCOUNT_ROLE" ->
                    "Financial transactions can only be executed on customer accounts.";
            case "BANK_ACCOUNT_NOT_CONFIGURED" ->
                    "The bank system fee account is not properly configured.";
            case "FEE_CONFIGURATION_INVALID" ->
                    "The transfer fee configuration is invalid.";
            case "MAX_ATTEMPTS_EXCEEDED" ->
                    "Transaction processing failed after exceeding the maximum number of retry attempts.";
            case "TRANSIENT_PROCESSING_ERROR" ->
                    "A temporary error occurred while processing the transaction; retry is scheduled.";
            default ->
                    "Transaction failed with error code: " + failureCode;
        };
    }
}

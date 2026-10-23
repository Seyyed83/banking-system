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
                List.copyOf(financialTransactions));
    }
}

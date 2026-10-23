package com.example.banking.transaction.dto;

import com.example.banking.transaction.entity.FinancialTransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionHistoryItem(
        Long transactionId,
        String trackingCode,
        String requestCode,
        FinancialTransactionType type,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        OffsetDateTime createdAt
) {
}
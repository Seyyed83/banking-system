package com.example.banking.transaction.dto;

import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.FinancialTransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FinancialTransactionResponse(
        String trackingCode,
        FinancialTransactionType type,
        BigDecimal amount,
        Long parentTransactionId,
        OffsetDateTime createdAt) {

    public static FinancialTransactionResponse from(FinancialTransaction transaction) {
        return new FinancialTransactionResponse(
                transaction.getTrackingCode(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getParentTransactionId(),
                transaction.getCreatedAt());
    }
}

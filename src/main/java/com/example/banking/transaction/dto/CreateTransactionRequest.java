package com.example.banking.transaction.dto;

import com.example.banking.transaction.entity.TransactionType;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        String clientId,
        String idempotencyKey,
        TransactionType type,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount) {
}

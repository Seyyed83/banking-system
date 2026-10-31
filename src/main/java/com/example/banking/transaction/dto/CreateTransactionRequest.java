package com.example.banking.transaction.dto;

import com.example.banking.transaction.entity.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @Schema(description = "Unique identifier of the calling client", example = "web-client-01")
        String clientId,

        @Schema(description = "Idempotency key to prevent duplicate execution on network retries", example = "idem-2026-0001")
        String idempotencyKey,

        @Schema(description = "Transaction type: DEPOSIT, WITHDRAWAL, or TRANSFER", example = "TRANSFER")
        TransactionType type,

        @Schema(description = "14-digit source account number (required for WITHDRAWAL and TRANSFER)", example = "12345678901234")
        String sourceAccountNumber,

        @Schema(description = "14-digit destination account number (required for DEPOSIT and TRANSFER)", example = "98765432109876")
        String destinationAccountNumber,

        @Schema(description = "Transaction amount (positive value up to 4 decimal places)", example = "500000.0000")
        BigDecimal amount) {
}

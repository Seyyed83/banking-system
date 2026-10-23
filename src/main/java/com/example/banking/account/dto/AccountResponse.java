package com.example.banking.account.dto;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.entity.AccountStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        String accountNumber,
        Long customerId,
        AccountRole accountRole,
        AccountStatus status,
        BigDecimal balance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getAccountRole(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
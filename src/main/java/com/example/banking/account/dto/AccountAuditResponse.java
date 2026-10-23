package com.example.banking.account.dto;

import com.example.banking.account.entity.AccountAudit;

import java.time.OffsetDateTime;

public record AccountAuditResponse(
        Long id,
        Long accountId,
        String changedBy,
        String fieldName,
        String oldValue,
        String newValue,
        OffsetDateTime changedAt
) {
    public static AccountAuditResponse from(AccountAudit audit) {
        return new AccountAuditResponse(
                audit.getId(),
                audit.getAccountId(),
                audit.getChangedBy(),
                audit.getFieldName(),
                audit.getOldValue(),
                audit.getNewValue(),
                audit.getChangedAt()
        );
    }
}
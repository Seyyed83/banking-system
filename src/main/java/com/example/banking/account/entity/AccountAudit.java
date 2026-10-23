package com.example.banking.account.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "ACCOUNT_AUDIT")
public class AccountAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "CHANGED_BY", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "FIELD_NAME", nullable = false, length = 100)
    private String fieldName;

    @Lob
    @Column(name = "OLD_VALUE")
    private String oldValue;

    @Lob
    @Column(name = "NEW_VALUE")
    private String newValue;

    @Column(name = "CHANGED_AT", nullable = false)
    private OffsetDateTime changedAt;

    protected AccountAudit() {
    }

    public AccountAudit(
            Long accountId,
            String changedBy,
            String fieldName,
            String oldValue,
            String newValue) {
        this.accountId = accountId;
        this.changedBy = changedBy;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PrePersist
    protected void beforeInsert() {
        if (changedAt == null) {
            changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public OffsetDateTime getChangedAt() {
        return changedAt;
    }
}
package com.example.banking.transaction.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "LEDGER_ENTRIES")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FINANCIAL_TRANSACTION_ID", nullable = false)
    private Long financialTransactionId;

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ENTRY_TYPE", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long financialTransactionId, Long accountId,
                       LedgerEntryType entryType, BigDecimal amount) {
        this.financialTransactionId = financialTransactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
    }

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() { return id; }
    public Long getFinancialTransactionId() { return financialTransactionId; }
    public Long getAccountId() { return accountId; }
    public LedgerEntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

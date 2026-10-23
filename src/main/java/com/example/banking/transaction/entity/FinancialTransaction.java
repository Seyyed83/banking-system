package com.example.banking.transaction.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "FINANCIAL_TRANSACTIONS")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REQUEST_ID", nullable = false)
    private Long requestId;

    @Column(name = "PARENT_TRANSACTION_ID")
    private Long parentTransactionId;

    @Column(name = "TRACKING_CODE", nullable = false, length = 64, unique = true)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 20)
    private FinancialTransactionType transactionType;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected FinancialTransaction() {
    }

    public FinancialTransaction(
            Long requestId,
            Long parentTransactionId,
            String trackingCode,
            FinancialTransactionType transactionType,
            BigDecimal amount) {
        this.requestId = requestId;
        this.parentTransactionId = parentTransactionId;
        this.trackingCode = trackingCode;
        this.transactionType = transactionType;
        this.amount = amount;
    }

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getParentTransactionId() { return parentTransactionId; }
    public String getTrackingCode() { return trackingCode; }
    public FinancialTransactionType getTransactionType() { return transactionType; }
    public BigDecimal getAmount() { return amount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

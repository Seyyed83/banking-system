package com.example.banking.transaction.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "TRANSACTION_REQUESTS")
public class TransactionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REQUEST_CODE", nullable = false, length = 64, unique = true)
    private String requestCode;

    @Column(name = "CLIENT_ID", nullable = false, length = 100)
    private String clientId;

    @Column(name = "IDEMPOTENCY_KEY", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "SOURCE_ACCOUNT_ID")
    private Long sourceAccountId;

    @Column(name = "DESTINATION_ACCOUNT_ID")
    private Long destinationAccountId;

    @Column(name = "AMOUNT", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TransactionRequestStatus status;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private int attemptCount;

    @Column(name = "WORKER_ID", length = 100)
    private String workerId;

    @Column(name = "LEASE_UNTIL")
    private OffsetDateTime leaseUntil;

    @Column(name = "NEXT_ATTEMPT_AT", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "PROCESSED_AT")
    private OffsetDateTime processedAt;

    @Column(name = "FAILURE_CODE", length = 100)
    private String failureCode;

    protected TransactionRequest() {
    }

    public TransactionRequest(
            String requestCode,
            String clientId,
            String idempotencyKey,
            TransactionType transactionType,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount) {
        this.requestCode = requestCode;
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
        this.transactionType = transactionType;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.status = TransactionRequestStatus.PENDING;
        this.attemptCount = 0;
    }

    @PrePersist
    protected void beforeInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
        if (status == null) {
            status = TransactionRequestStatus.PENDING;
        }
    }

    // Claiming (PENDING/expired PROCESSING -> PROCESSING) is done exclusively by the atomic SQL
    // updates in TransactionRequestRepository (claimById / claimNext), never by this entity.

    public void markCompleted() {
        status = TransactionRequestStatus.COMPLETED;
        processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        workerId = null;
        leaseUntil = null;
        failureCode = null;
    }

    public void markFailed(String code) {
        status = TransactionRequestStatus.FAILED;
        if (attemptCount == 0) {
            attemptCount = 1;
        }
        processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        workerId = null;
        leaseUntil = null;
        failureCode = code;
    }

    public void scheduleRetry(
            OffsetDateTime nextAttemptAt,
            String failureCode) {

        this.status = TransactionRequestStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.workerId = null;
        this.leaseUntil = null;
        this.processedAt = null;
        this.failureCode = failureCode;
    }

    public Long getId() { return id; }
    public String getRequestCode() { return requestCode; }
    public String getClientId() { return clientId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransactionType getTransactionType() { return transactionType; }
    public Long getSourceAccountId() { return sourceAccountId; }
    public Long getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionRequestStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getWorkerId() { return workerId; }
    public OffsetDateTime getLeaseUntil() { return leaseUntil; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public String getFailureCode() { return failureCode; }
}

package com.example.banking.account.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "ACCOUNTS",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_ACCOUNT_NUMBER",
                columnNames = "ACCOUNT_NUMBER"
        )
)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(
            name = "ACCOUNT_NUMBER",
            nullable = false,
            length = 14
    )
    private String accountNumber;

    @Column(name = "CUSTOMER_ID")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "ACCOUNT_ROLE",
            nullable = false,
            length = 20
    )
    private AccountRole accountRole;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "STATUS",
            nullable = false,
            length = 20
    )
    private AccountStatus status;

    @Column(
            name = "BALANCE",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal balance;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private OffsetDateTime updatedAt;

    protected Account() {
        // Required by JPA
    }

    private Account(
            String accountNumber,
            Long customerId,
            AccountRole accountRole,
            AccountStatus status
    ) {
        validateAccountNumber(accountNumber);

        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.accountRole = accountRole;
        this.status = status;
        this.balance = BigDecimal.ZERO;
    }

    public static Account openCustomerAccount(
            String accountNumber,
            Long customerId
    ) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException(
                    "A valid customer ID is required."
            );
        }

        return new Account(
                accountNumber,
                customerId,
                AccountRole.CUSTOMER,
                AccountStatus.ACTIVE
        );
    }

    public static Account openSystemAccount(
            String accountNumber
    ) {
        return new Account(
                accountNumber,
                null,
                AccountRole.SYSTEM,
                AccountStatus.ACTIVE
        );
    }

    private static void validateAccountNumber(
            String accountNumber
    ) {
        if (
                accountNumber == null
                        || !accountNumber.matches("[0-9]{14}")
        ) {
            throw new IllegalArgumentException(
                    "Account number must contain exactly 14 digits."
            );
        }
    }

    public void changeStatus(AccountStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "Account status is required."
            );
        }

        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void credit(BigDecimal amount) {
        validateAmount(amount);
        requireActive();

        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        validateAmount(amount);
        requireActive();

        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient account balance."
            );
        }

        this.balance = this.balance.subtract(amount);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero."
            );
        }

        if (amount.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException(
                    "Amount cannot have more than 4 decimal places."
            );
        }
    }

    private void requireActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Financial operations require an active account."
            );
        }
    }

    @PrePersist
    protected void beforeInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (this.createdAt == null) {
            this.createdAt = now;
        }

        this.updatedAt = now;
    }

    // UPDATED_AT is the "last edit of the account" shown to users (task section 2-3). It is touched
    // when the status changes (changeStatus) and when customer details are edited
    // (AccountDetailsUpdateService), but NOT by deposits/withdrawals/transfers: balance movements
    // are recorded in the ledger and are not edits of the account.

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public AccountRole getAccountRole() {
        return accountRole;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
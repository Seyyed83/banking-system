package com.example.banking.transaction.repository;

import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.FinancialTransactionType;
import com.example.banking.transaction.entity.LedgerEntry;
import com.example.banking.transaction.entity.LedgerEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface FinancialTransactionRepository
        extends JpaRepository<FinancialTransaction, Long> {

    List<FinancialTransaction> findByRequestIdOrderByCreatedAtAsc(
            Long requestId);

    @Query(
            value = """
            SELECT ft
            FROM FinancialTransaction ft
            WHERE
                (:accountId IS NULL OR EXISTS (
                    SELECT le.id
                    FROM LedgerEntry le
                    WHERE le.financialTransactionId = ft.id
                      AND le.accountId = :accountId
                ))
                AND (:type IS NULL OR ft.transactionType = :type)
                AND (:sourceAccountId IS NULL OR EXISTS (
                    SELECT le.id
                    FROM LedgerEntry le
                    WHERE le.financialTransactionId = ft.id
                      AND le.accountId = :sourceAccountId
                      AND le.entryType = :debitType
                ))
                AND (:destinationAccountId IS NULL OR EXISTS (
                    SELECT le.id
                    FROM LedgerEntry le
                    WHERE le.financialTransactionId = ft.id
                      AND le.accountId = :destinationAccountId
                      AND le.entryType = :creditType
                ))
                AND (:minAmount IS NULL OR ft.amount >= :minAmount)
                AND (:maxAmount IS NULL OR ft.amount <= :maxAmount)
                AND (:fromTime IS NULL OR ft.createdAt >= :fromTime)
                AND (:toTime IS NULL OR ft.createdAt <= :toTime)
            ORDER BY ft.createdAt DESC, ft.id DESC
            """,
            countQuery = """
            SELECT COUNT(ft)
            FROM FinancialTransaction ft
            WHERE
                (:accountId IS NULL OR EXISTS (
                    SELECT le.id
                    FROM LedgerEntry le
                    WHERE le.financialTransactionId = ft.id
                      AND le.accountId = :accountId
                ))
                AND (:type IS NULL OR ft.transactionType = :type)
                AND (:sourceAccountId IS NULL OR EXISTS (
                    SELECT le.id
                    FROM LedgerEntry le
                    WHERE le.financialTransactionId = ft.id
                      AND le.accountId = :sourceAccountId
                      AND le.entryType = :debitType
                ))
                AND (:destinationAccountId IS NULL OR EXISTS (
                    SELECT le.id
                    FROM LedgerEntry le
                    WHERE le.financialTransactionId = ft.id
                      AND le.accountId = :destinationAccountId
                      AND le.entryType = :creditType
                ))
                AND (:minAmount IS NULL OR ft.amount >= :minAmount)
                AND (:maxAmount IS NULL OR ft.amount <= :maxAmount)
                AND (:fromTime IS NULL OR ft.createdAt >= :fromTime)
                AND (:toTime IS NULL OR ft.createdAt <= :toTime)
            """
    )
    Page<FinancialTransaction> searchHistory(
            @Param("accountId") Long accountId,
            @Param("type") FinancialTransactionType type,
            @Param("sourceAccountId") Long sourceAccountId,
            @Param("destinationAccountId") Long destinationAccountId,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime,
            @Param("debitType") LedgerEntryType debitType,
            @Param("creditType") LedgerEntryType creditType,
            Pageable pageable
    );
}
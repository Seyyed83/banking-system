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

    List<FinancialTransaction> findByRequestIdOrderByCreatedAtAsc(Long requestId);

    @Query(
        value = """
            select ft
            from FinancialTransaction ft
            where
                (:accountId is null or exists (
                    select le.id
                    from LedgerEntry le
                    where le.financialTransactionId = ft.id
                      and le.accountId = :accountId
                ))
                and (:type is null or ft.transactionType = :type)
                and (:sourceAccountId is null or exists (
                    select debit.id
                    from LedgerEntry debit
                    where debit.financialTransactionId = ft.id
                      and debit.entryType = :debitType
                      and debit.accountId = :sourceAccountId
                ))
                and (:destinationAccountId is null or exists (
                    select credit.id
                    from LedgerEntry credit
                    where credit.financialTransactionId = ft.id
                      and credit.entryType = :creditType
                      and credit.accountId = :destinationAccountId
                ))
                and (:minAmount is null or ft.amount >= :minAmount)
                and (:maxAmount is null or ft.amount <= :maxAmount)
                and (:fromTime is null or ft.createdAt >= :fromTime)
                and (:toTime is null or ft.createdAt <= :toTime)
            order by ft.createdAt desc, ft.id desc
            """,
        countQuery = """
            select count(ft.id)
            from FinancialTransaction ft
            where
                (:accountId is null or exists (
                    select le.id
                    from LedgerEntry le
                    where le.financialTransactionId = ft.id
                      and le.accountId = :accountId
                ))
                and (:type is null or ft.transactionType = :type)
                and (:sourceAccountId is null or exists (
                    select debit.id
                    from LedgerEntry debit
                    where debit.financialTransactionId = ft.id
                      and debit.entryType = :debitType
                      and debit.accountId = :sourceAccountId
                ))
                and (:destinationAccountId is null or exists (
                    select credit.id
                    from LedgerEntry credit
                    where credit.financialTransactionId = ft.id
                      and credit.entryType = :creditType
                      and credit.accountId = :destinationAccountId
                ))
                and (:minAmount is null or ft.amount >= :minAmount)
                and (:maxAmount is null or ft.amount <= :maxAmount)
                and (:fromTime is null or ft.createdAt >= :fromTime)
                and (:toTime is null or ft.createdAt <= :toTime)
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
package com.example.banking.transaction.repository;

import com.example.banking.transaction.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByFinancialTransactionIdIn(Collection<Long> financialTransactionIds);
    List<LedgerEntry> findByAccountId(Long accountId);
}

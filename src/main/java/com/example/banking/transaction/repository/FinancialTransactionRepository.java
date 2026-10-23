package com.example.banking.transaction.repository;

import com.example.banking.transaction.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    List<FinancialTransaction> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}

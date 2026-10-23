package com.example.banking.account.repository;

import com.example.banking.account.entity.AccountAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountAuditRepository
        extends JpaRepository<AccountAudit, Long> {

    List<AccountAudit> findByAccountIdOrderByChangedAtDesc(Long accountId);
}
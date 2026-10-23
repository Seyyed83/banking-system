package com.example.banking.account.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountManagementService {

    private final AccountRepository accountRepository;
    private final AccountAuditRepository auditRepository;

    public AccountManagementService(
            AccountRepository accountRepository,
            AccountAuditRepository auditRepository) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public Account getById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid account ID.");
        }

        return accountRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found."));
    }

    @Transactional(readOnly = true)
    public Account getByAccountNumber(String accountNumber) {
        if (accountNumber == null
                || !accountNumber.matches("[0-9]{14}")) {
            throw new IllegalArgumentException(
                    "Account number must contain exactly 14 digits.");
        }

        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found."));
    }

    @Transactional(readOnly = true)
    public Page<Account> getCustomerAccounts(
            Long customerId,
            AccountStatus status,
            int page,
            int size) {

        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("Invalid customer ID.");
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative.");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100.");
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "id")
        );

        if (status != null) {
            return accountRepository.findByCustomerIdAndStatus(
                    customerId, status, pageable);
        }

        return accountRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional
    public Account changeStatus(
            Long accountId,
            AccountStatus newStatus,
            String changedBy) {

        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Invalid account ID.");
        }

        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "New account status is required.");
        }

        if (changedBy == null || changedBy.isBlank()) {
            throw new IllegalArgumentException(
                    "The actor making the change is required.");
        }

        String actor = changedBy.trim();

        if (actor.length() > 100) {
            throw new IllegalArgumentException(
                    "Actor name cannot exceed 100 characters.");
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found."));

        AccountStatus oldStatus = account.getStatus();

        if (oldStatus == newStatus) {
            return account;
        }

        account.changeStatus(newStatus);

        auditRepository.save(new AccountAudit(
                account.getId(),
                actor,
                "STATUS",
                oldStatus.name(),
                newStatus.name()
        ));

        return accountRepository.save(account);
    }
}
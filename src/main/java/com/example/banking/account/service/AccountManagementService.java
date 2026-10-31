package com.example.banking.account.service;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.cache.AccountBalanceCache;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.common.logging.LogMasker;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AccountManagementService {

    private static final Logger log =
            LoggerFactory.getLogger(AccountManagementService.class);

    private final AccountRepository accountRepository;
    private final AccountAuditRepository auditRepository;
    private final CustomerRepository customerRepository;
    private final AccountBalanceCache balanceCache;

    public AccountManagementService(
            AccountRepository accountRepository,
            AccountAuditRepository auditRepository) {
        this(accountRepository, auditRepository, null);
    }

    public AccountManagementService(
            AccountRepository accountRepository,
            AccountAuditRepository auditRepository,
            CustomerRepository customerRepository) {
        this(accountRepository, auditRepository, customerRepository, null);
    }

    @Autowired
    public AccountManagementService(
            AccountRepository accountRepository,
            AccountAuditRepository auditRepository,
            CustomerRepository customerRepository,
            AccountBalanceCache balanceCache) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.customerRepository = customerRepository;
        this.balanceCache = balanceCache;
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

    /**
     * Retrieves full customer registration details, account status, balance and timestamps
     * by account ID (Task section 2-3).
     */
    @Transactional(readOnly = true)
    public AccountDetailsResponse getDetailsById(Long id) {
        Account account = getById(id);
        return toAccountDetailsResponse(account);
    }

    /**
     * Retrieves full customer registration details, account status, balance and timestamps
     * by 14-digit account number (Task section 2-3).
     */
    @Transactional(readOnly = true)
    public AccountDetailsResponse getDetailsByAccountNumber(String accountNumber) {
        Account account = getByAccountNumber(accountNumber);
        return toAccountDetailsResponse(account);
    }

    /**
     * Retrieves customer account details (including account number) by national ID,
     * legal entity ID, or foreign identity number (Task section 2-3).
     */
    @Transactional(readOnly = true)
    public AccountDetailsResponse getAccountByCustomerIdentity(
            String identityNumber,
            String identityType) {
        if (identityNumber == null || identityNumber.isBlank()) {
            throw new IllegalArgumentException("Identity number is required.");
        }
        if (customerRepository == null) {
            throw new IllegalStateException("CustomerRepository is not configured.");
        }

        String normalizedIdNumber = identityNumber.trim();
        Customer customer;
        if (identityType != null && !identityType.isBlank()) {
            String normalizedIdType = identityType.trim().toUpperCase(Locale.ROOT);
            customer = customerRepository
                    .findByIdentityTypeAndIdentityNumber(normalizedIdType, normalizedIdNumber)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found for the given identity."));
        } else {
            customer = customerRepository
                    .findFirstByIdentityNumberOrderByIdAsc(normalizedIdNumber)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Customer not found for the given identity number."));
        }

        Account account = accountRepository
                .findFirstByCustomerIdOrderByIdAsc(customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found for this customer."));

        return AccountDetailsResponse.of(account, customer);
    }

    private AccountDetailsResponse toAccountDetailsResponse(Account account) {
        if (account.getAccountRole() != AccountRole.CUSTOMER || account.getCustomerId() == null) {
            throw new IllegalArgumentException(
                    "Customer details are only available for customer accounts.");
        }
        if (customerRepository == null) {
            throw new IllegalStateException("CustomerRepository is not configured.");
        }
        Customer customer = customerRepository.findById(account.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found."));
        return AccountDetailsResponse.of(account, customer);
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

        Account saved = accountRepository.save(account);
        if (balanceCache != null) {
            // The cached balance view also carries the status; drop it once this change commits.
            balanceCache.evictAfterCommit(java.util.List.of(account.getAccountNumber()));
        }
        log.info("Changed account status accountId={}, accountNumber={}, oldStatus={}, newStatus={}, changedBy={}",
                account.getId(),
                LogMasker.maskAccountNumber(account.getAccountNumber()),
                oldStatus,
                newStatus,
                actor);
        return saved;
    }
}
package com.example.banking.transaction.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.transaction.dto.TransactionHistoryItem;
import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.FinancialTransactionType;
import com.example.banking.transaction.entity.LedgerEntry;
import com.example.banking.transaction.entity.LedgerEntryType;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.repository.FinancialTransactionRepository;
import com.example.banking.transaction.repository.LedgerEntryRepository;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TransactionHistoryService {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRequestRepository transactionRequestRepository;

    public TransactionHistoryService(
            AccountRepository accountRepository,
            FinancialTransactionRepository financialTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            TransactionRequestRepository transactionRequestRepository) {
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRequestRepository = transactionRequestRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransactionHistoryItem> search(
            String accountNumber,
            FinancialTransactionType type,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            OffsetDateTime fromTime,
            OffsetDateTime toTime,
            int page,
            int size) {

        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100.");
        }
        if (minAmount != null && minAmount.signum() < 0) {
            throw new IllegalArgumentException("Minimum amount cannot be negative.");
        }
        if (maxAmount != null && maxAmount.signum() < 0) {
            throw new IllegalArgumentException("Maximum amount cannot be negative.");
        }
        if (minAmount != null && maxAmount != null
                && minAmount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException(
                    "Minimum amount cannot exceed maximum amount.");
        }
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new IllegalArgumentException(
                    "Start time cannot be after end time.");
        }

        Long accountId = resolveAccountId(accountNumber, "Account");
        Long sourceAccountId = resolveAccountId(sourceAccountNumber, "Source");
        Long destinationAccountId =
                resolveAccountId(destinationAccountNumber, "Destination");

        Page<FinancialTransaction> resultPage =
                financialTransactionRepository.searchHistory(
                        accountId,
                        type,
                        sourceAccountId,
                        destinationAccountId,
                        minAmount,
                        maxAmount,
                        fromTime,
                        toTime,
                        LedgerEntryType.DEBIT,
                        LedgerEntryType.CREDIT,
                        PageRequest.of(page, size));

        List<FinancialTransaction> transactions = resultPage.getContent();
        if (transactions.isEmpty()) {
            return new PageImpl<>(
                    List.of(), resultPage.getPageable(), resultPage.getTotalElements());
        }

        List<Long> transactionIds = transactions.stream()
                .map(FinancialTransaction::getId)
                .toList();

        List<LedgerEntry> entries =
                ledgerEntryRepository.findByFinancialTransactionIdIn(transactionIds);

        Map<Long, Long> sourceIds = new HashMap<>();
        Map<Long, Long> destinationIds = new HashMap<>();
        Set<Long> involvedAccountIds = new HashSet<>();
        Set<Long> requestIds = new HashSet<>();

        for (LedgerEntry entry : entries) {
            involvedAccountIds.add(entry.getAccountId());

            if (entry.getEntryType() == LedgerEntryType.DEBIT) {
                sourceIds.putIfAbsent(
                        entry.getFinancialTransactionId(), entry.getAccountId());
            } else if (entry.getEntryType() == LedgerEntryType.CREDIT) {
                destinationIds.putIfAbsent(
                        entry.getFinancialTransactionId(), entry.getAccountId());
            }
        }

        for (FinancialTransaction transaction : transactions) {
            requestIds.add(transaction.getRequestId());
        }

        Map<Long, Account> accountsById = new HashMap<>();
        if (!involvedAccountIds.isEmpty()) {
            for (Account account : accountRepository.findAllById(involvedAccountIds)) {
                accountsById.put(account.getId(), account);
            }
        }

        Map<Long, TransactionRequest> requestsById = new HashMap<>();
        if (!requestIds.isEmpty()) {
            for (TransactionRequest request :
                    transactionRequestRepository.findAllById(requestIds)) {
                requestsById.put(request.getId(), request);
            }
        }

        List<TransactionHistoryItem> items = new ArrayList<>(transactions.size());

        for (FinancialTransaction transaction : transactions) {
            TransactionRequest request =
                    requestsById.get(transaction.getRequestId());

            if (request == null) {
                throw new IllegalStateException(
                        "Financial transaction references a missing request.");
            }

            items.add(new TransactionHistoryItem(
                    transaction.getId(),
                    transaction.getTrackingCode(),
                    request.getRequestCode(),
                    transaction.getTransactionType(),
                    accountNumberFor(sourceIds.get(transaction.getId()), accountsById),
                    accountNumberFor(destinationIds.get(transaction.getId()), accountsById),
                    transaction.getAmount(),
                    transaction.getCreatedAt()
            ));
        }

        return new PageImpl<>(
                items, resultPage.getPageable(), resultPage.getTotalElements());
    }

    private Long resolveAccountId(String rawNumber, String label) {
        if (rawNumber == null) {
            return null;
        }

        String number = rawNumber.trim();
        if (!number.matches("[0-9]{14}")) {
            throw new IllegalArgumentException(
                    label + " account number must contain exactly 14 digits.");
        }

        return accountRepository.findByAccountNumber(number)
                .map(Account::getId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        label + " account was not found."));
    }

    private String accountNumberFor(
            Long accountId, Map<Long, Account> accountsById) {
        if (accountId == null) {
            return null;
        }
        Account account = accountsById.get(accountId);
        return account == null ? null : account.getAccountNumber();
    }
}
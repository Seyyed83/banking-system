package com.example.banking.transaction.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.transaction.entity.*;
import com.example.banking.transaction.repository.FinancialTransactionRepository;
import com.example.banking.transaction.repository.LedgerEntryRepository;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionExecutionService {

    private final TransactionRequestRepository requestRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Value("${banking.system-account-number:99999999999999}")
    private String systemAccountNumber;

    @Value("${banking.transfer-fee.rate:0.001}")
    private BigDecimal feeRate;

    @Value("${banking.transfer-fee.minimum:500}")
    private BigDecimal minimumFee;

    @Value("${banking.transfer-fee.maximum:10000}")
    private BigDecimal maximumFee;

    public TransactionExecutionService(TransactionRequestRepository requestRepository,
                                       AccountRepository accountRepository,
                                       FinancialTransactionRepository financialTransactionRepository,
                                       LedgerEntryRepository ledgerEntryRepository) {
        this.requestRepository = requestRepository;
        this.accountRepository = accountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void execute(Long requestId, String workerId) {
        TransactionRequest request = requestRepository
                .findByIdForUpdate(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Transaction request not found."));

        if (request.getStatus() != TransactionRequestStatus.PROCESSING
                || !java.util.Objects.equals(
                request.getWorkerId(), workerId)) {
            return;
        }

        switch (request.getTransactionType()) {
            case DEPOSIT -> executeDeposit(request);
            case WITHDRAWAL -> executeWithdrawal(request);
            case TRANSFER -> executeTransfer(request);
        }

        request.markCompleted();
    }

    private void executeDeposit(TransactionRequest request) {
        Account account = lockAccounts(List.of(request.getDestinationAccountId()))
                .get(request.getDestinationAccountId());
        requireCustomerAccount(account);
        safeCredit(account, request.getAmount());

        FinancialTransaction transaction = saveFinancialTransaction(
                request, null, FinancialTransactionType.DEPOSIT, request.getAmount());
        saveLedger(transaction, account, LedgerEntryType.CREDIT, request.getAmount());
    }

    private void executeWithdrawal(TransactionRequest request) {
        Account account = lockAccounts(List.of(request.getSourceAccountId()))
                .get(request.getSourceAccountId());
        requireCustomerAccount(account);
        safeDebit(account, request.getAmount());

        FinancialTransaction transaction = saveFinancialTransaction(
                request, null, FinancialTransactionType.WITHDRAWAL, request.getAmount());
        saveLedger(transaction, account, LedgerEntryType.DEBIT, request.getAmount());
    }

    private void executeTransfer(TransactionRequest request) {
        // Resolve the bank account ID with a scalar query so that NO Account entity is loaded
        // before the row locks are taken. Reading entities first (e.g. findById) would leave
        // stale balances in the persistence context, and a later "SELECT ... FOR UPDATE" does
        // not refresh already-managed entities -> lost update / overdraft under concurrency.
        Long systemAccountId = accountRepository.findIdByAccountNumber(systemAccountNumber)
                .orElseThrow(() -> new FinancialOperationException(
                        "BANK_ACCOUNT_NOT_CONFIGURED", "The bank fee account is not configured."));

        // Every account touched by a transfer is locked in ascending ID order to reduce deadlocks.
        Map<Long, Account> locked = lockAccounts(List.of(
                request.getSourceAccountId(), request.getDestinationAccountId(), systemAccountId));
        Account source = locked.get(request.getSourceAccountId());
        Account destination = locked.get(request.getDestinationAccountId());
        Account system = locked.get(systemAccountId);
        requireCustomerAccount(source);
        requireCustomerAccount(destination);
        if (system.getAccountRole() != AccountRole.SYSTEM) {
            throw new FinancialOperationException(
                    "BANK_ACCOUNT_NOT_CONFIGURED", "Configured bank fee account is not a system account.");
        }

        BigDecimal fee = calculateFee(request.getAmount());
        safeDebit(source, request.getAmount().add(fee));
        safeCredit(destination, request.getAmount());
        safeCredit(system, fee);

        FinancialTransaction transfer = saveFinancialTransaction(
                request, null, FinancialTransactionType.TRANSFER, request.getAmount());
        saveLedger(transfer, source, LedgerEntryType.DEBIT, request.getAmount());
        saveLedger(transfer, destination, LedgerEntryType.CREDIT, request.getAmount());

        FinancialTransaction feeTransaction = saveFinancialTransaction(
                request, transfer.getId(), FinancialTransactionType.FEE, fee);
        saveLedger(feeTransaction, source, LedgerEntryType.DEBIT, fee);
        saveLedger(feeTransaction, system, LedgerEntryType.CREDIT, fee);
    }

    private Map<Long, Account> lockAccounts(List<Long> ids) {
        Map<Long, Account> accounts = new LinkedHashMap<>();
        ids.stream().distinct().sorted().forEach(id -> {
            Account account = accountRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new FinancialOperationException("ACCOUNT_NOT_FOUND", "Account was not found."));
            accounts.put(id, account);
        });
        return accounts;
    }

    private void requireCustomerAccount(Account account) {
        if (account.getAccountRole() != AccountRole.CUSTOMER) {
            throw new FinancialOperationException(
                    "INVALID_ACCOUNT_ROLE", "Financial requests can only use customer accounts.");
        }
    }

    private void safeDebit(Account account, BigDecimal amount) {
        try {
            account.debit(amount);
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("Insufficient")) {
                throw new FinancialOperationException("INSUFFICIENT_FUNDS", "Insufficient account balance for this transaction.");
            }
            throw new FinancialOperationException("ACCOUNT_NOT_ACTIVE", "Source account is not active.");
        }
    }

    private void safeCredit(Account account, BigDecimal amount) {
        try {
            account.credit(amount);
        } catch (IllegalStateException exception) {
            throw new FinancialOperationException("ACCOUNT_NOT_ACTIVE", "Destination account is not active.");
        }
    }

    private BigDecimal calculateFee(BigDecimal amount) {
        if (feeRate == null || minimumFee == null || maximumFee == null
                || feeRate.signum() < 0 || minimumFee.signum() <= 0
                || maximumFee.signum() <= 0 || maximumFee.compareTo(minimumFee) < 0) {
            throw new FinancialOperationException("FEE_CONFIGURATION_INVALID", "Transfer fee configuration is invalid.");
        }
        BigDecimal fee = amount.multiply(feeRate).setScale(4, RoundingMode.HALF_UP);
        if (fee.compareTo(minimumFee) < 0) {
            fee = minimumFee;
        }
        if (fee.compareTo(maximumFee) > 0) {
            fee = maximumFee;
        }
        return fee.setScale(4, RoundingMode.HALF_UP);
    }

    private FinancialTransaction saveFinancialTransaction(
            TransactionRequest request,
            Long parentId,
            FinancialTransactionType type,
            BigDecimal amount) {
        return financialTransactionRepository.saveAndFlush(new FinancialTransaction(
                request.getId(), parentId, "FTX-" + UUID.randomUUID(), type, amount));
    }

    private void saveLedger(FinancialTransaction transaction, Account account,
                            LedgerEntryType type, BigDecimal amount) {
        ledgerEntryRepository.save(new LedgerEntry(
                transaction.getId(), account.getId(), type, amount));
    }
}

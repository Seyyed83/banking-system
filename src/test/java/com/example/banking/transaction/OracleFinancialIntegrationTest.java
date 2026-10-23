package com.example.banking.transaction;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ConflictException;
import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.FinancialTransactionType;
import com.example.banking.transaction.entity.LedgerEntry;
import com.example.banking.transaction.entity.LedgerEntryType;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.entity.TransactionType;
import com.example.banking.transaction.repository.FinancialTransactionRepository;
import com.example.banking.transaction.repository.LedgerEntryRepository;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import com.example.banking.transaction.service.TransactionProcessor;
import com.example.banking.transaction.service.TransactionSubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real Oracle integration tests. Opt in with -Doracle.integration=true. */
@SpringBootTest
@EnabledIfSystemProperty(named = "oracle.integration", matches = "true")
class OracleFinancialIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionSubmissionService submissionService;
    @Autowired private TransactionProcessor processor;
    @Autowired private TransactionRequestRepository requestRepository;
    @Autowired private FinancialTransactionRepository financialTransactionRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;

    @Value("${banking.system-account-number:99999999999999}")
    private String systemAccountNumber;

    @Test
    void depositAtomicallyIncreasesBalanceAndWritesCreditLedgerEntry() {
        Account destination = newCustomerAccount(BigDecimal.ZERO);

        TransactionRequest request = submit("DEPOSIT", TransactionType.DEPOSIT,
                null, destination.getAccountNumber(), "275.2500");
        processor.processRequest(request.getId());

        TransactionRequest finished = getRequest(request.getId());
        assertEquals(TransactionRequestStatus.COMPLETED, finished.getStatus());
        assertMoney("275.2500", accountRepository.findById(destination.getId()).orElseThrow().getBalance());

        List<FinancialTransaction> transactions = financialTransactions(request);
        assertEquals(1, transactions.size());
        assertEquals(FinancialTransactionType.DEPOSIT, transactions.getFirst().getTransactionType());
        List<LedgerEntry> entries = ledgerEntries(transactions);
        assertEquals(1, entries.size());
        assertEquals(LedgerEntryType.CREDIT, entries.getFirst().getEntryType());
        assertMoney("275.2500", entries.getFirst().getAmount());
    }

    @Test
    void successfulWithdrawalUpdatesBalanceAndWritesDebitLedgerEntry() {
        Account source = newCustomerAccount(new BigDecimal("800.0000"));

        TransactionRequest request = submit("WITHDRAWAL", TransactionType.WITHDRAWAL,
                source.getAccountNumber(), null, "300.0000");
        processor.processRequest(request.getId());

        assertEquals(TransactionRequestStatus.COMPLETED, getRequest(request.getId()).getStatus());
        assertMoney("500.0000", accountRepository.findById(source.getId()).orElseThrow().getBalance());
        List<FinancialTransaction> transactions = financialTransactions(request);
        assertEquals(1, transactions.size());
        assertEquals(FinancialTransactionType.WITHDRAWAL, transactions.getFirst().getTransactionType());
        List<LedgerEntry> entries = ledgerEntries(transactions);
        assertEquals(1, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.getFirst().getEntryType());
        assertMoney("300.0000", entries.getFirst().getAmount());
    }

    @Test
    void insufficientFundsMarkRequestFailedWithoutPartialWrites() {
        Account source = newCustomerAccount(new BigDecimal("600.0000"));

        TransactionRequest request = submit("WITHDRAWAL", TransactionType.WITHDRAWAL,
                source.getAccountNumber(), null, "700.0000");
        processor.processRequest(request.getId());

        TransactionRequest failed = getRequest(request.getId());
        assertEquals(TransactionRequestStatus.FAILED, failed.getStatus());
        assertEquals("INSUFFICIENT_FUNDS", failed.getFailureCode());
        assertMoney("600.0000", accountRepository.findById(source.getId()).orElseThrow().getBalance());
        assertTrue(financialTransactions(request).isEmpty());
    }

    @Test
    void transferAtomicallyUpdatesBalancesAndPersistsTransferAndFeeLedgerRows() {
        Account source = newCustomerAccount(new BigDecimal("10000.0000"));
        Account destination = newCustomerAccount(BigDecimal.ZERO);
        BigDecimal bankBalanceBefore = accountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow().getBalance();

        TransactionRequest request = submit("TRANSFER", TransactionType.TRANSFER,
                source.getAccountNumber(), destination.getAccountNumber(), "1000.0000");
        processor.processRequest(request.getId());

        TransactionRequest finished = getRequest(request.getId());
        assertEquals(TransactionRequestStatus.COMPLETED, finished.getStatus());
        assertMoney("8500.0000", accountRepository.findById(source.getId()).orElseThrow().getBalance());
        assertMoney("1000.0000", accountRepository.findById(destination.getId()).orElseThrow().getBalance());
        assertMoney(bankBalanceBefore.add(new BigDecimal("500.0000")).toPlainString(),
                accountRepository.findByAccountNumber(systemAccountNumber).orElseThrow().getBalance());

        List<FinancialTransaction> transactions = financialTransactions(request);
        assertEquals(2, transactions.size());
        assertEquals(4, ledgerEntries(transactions).size());
        FinancialTransaction transfer = transactions.stream()
                .filter(tx -> tx.getTransactionType() == FinancialTransactionType.TRANSFER)
                .findFirst().orElseThrow();
        FinancialTransaction fee = transactions.stream()
                .filter(tx -> tx.getTransactionType() == FinancialTransactionType.FEE)
                .findFirst().orElseThrow();
        assertEquals(transfer.getId(), fee.getParentTransactionId());
        assertMoney("1000.0000", transfer.getAmount());
        assertMoney("500.0000", fee.getAmount());
    }

    @Test
    void transferInsufficientForAmountPlusFeeChangesNoAccountAndWritesNoLedgerRows() {
        Account source = newCustomerAccount(new BigDecimal("1200.0000"));
        Account destination = newCustomerAccount(new BigDecimal("100.0000"));
        BigDecimal bankBefore = accountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow().getBalance();

        TransactionRequest request = submit("TRANSFER", TransactionType.TRANSFER,
                source.getAccountNumber(), destination.getAccountNumber(), "800.0000");
        processor.processRequest(request.getId());

        TransactionRequest failed = getRequest(request.getId());
        assertEquals(TransactionRequestStatus.FAILED, failed.getStatus());
        assertEquals("INSUFFICIENT_FUNDS", failed.getFailureCode());
        assertMoney("1200.0000", accountRepository.findById(source.getId()).orElseThrow().getBalance());
        assertMoney("100.0000", accountRepository.findById(destination.getId()).orElseThrow().getBalance());
        assertMoney(bankBefore.toPlainString(),
                accountRepository.findByAccountNumber(systemAccountNumber).orElseThrow().getBalance());
        assertTrue(financialTransactions(request).isEmpty());
    }

    @Test
    void repeatedIdempotencyKeyWithSamePayloadReturnsSameRequestAndDoesNotDuplicateFinancialRows() {
        Account destination = newCustomerAccount(BigDecimal.ZERO);
        String client = "oracle-it-client-" + UUID.randomUUID();
        String key = "idem-" + UUID.randomUUID();
        CreateTransactionRequest payload = new CreateTransactionRequest(
                client, key, TransactionType.DEPOSIT, null,
                destination.getAccountNumber(), new BigDecimal("125.0000"));

        var first = submissionService.submit(payload);
        var retry = submissionService.submit(payload);
        assertTrue(first.created());
        assertFalse(retry.created());
        assertEquals(first.request().getId(), retry.request().getId());
        assertEquals(first.request().getRequestCode(), retry.request().getRequestCode());

        processor.processRequest(first.request().getId());
        processor.processRequest(retry.request().getId());

        assertMoney("125.0000", accountRepository.findById(destination.getId()).orElseThrow().getBalance());
        assertEquals(1, financialTransactions(getRequest(first.request().getId())).size());
    }

    @Test
    void reusingIdempotencyKeyWithDifferentAmountIsRejected() {
        Account destination = newCustomerAccount(BigDecimal.ZERO);
        String client = "oracle-it-client-" + UUID.randomUUID();
        String key = "idem-" + UUID.randomUUID();
        submissionService.submit(new CreateTransactionRequest(client, key,
                TransactionType.DEPOSIT, null, destination.getAccountNumber(), new BigDecimal("10.0000")));

        assertThrows(ConflictException.class, () -> submissionService.submit(
                new CreateTransactionRequest(client, key, TransactionType.DEPOSIT,
                        null, destination.getAccountNumber(), new BigDecimal("11.0000"))));
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawAccount() throws Exception {
        Account source = newCustomerAccount(new BigDecimal("1000.0000"));
        TransactionRequest first = submit("WITHDRAWAL", TransactionType.WITHDRAWAL,
                source.getAccountNumber(), null, "700.0000");
        TransactionRequest second = submit("WITHDRAWAL", TransactionType.WITHDRAWAL,
                source.getAccountNumber(), null, "700.0000");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var f1 = executor.submit(() -> { start.await(); processor.processRequest(first.getId()); return null; });
            var f2 = executor.submit(() -> { start.await(); processor.processRequest(second.getId()); return null; });
            start.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        TransactionRequest firstResult = getRequest(first.getId());
        TransactionRequest secondResult = getRequest(second.getId());
        List<TransactionRequest> results = List.of(firstResult, secondResult);
        assertEquals(1, results.stream().filter(r -> r.getStatus() == TransactionRequestStatus.COMPLETED).count());
        assertEquals(1, results.stream().filter(r -> r.getStatus() == TransactionRequestStatus.FAILED).count());
        assertEquals("INSUFFICIENT_FUNDS", results.stream()
                .filter(r -> r.getStatus() == TransactionRequestStatus.FAILED)
                .findFirst().orElseThrow().getFailureCode());
        assertMoney("300.0000", accountRepository.findById(source.getId()).orElseThrow().getBalance());
        assertEquals(1, results.stream().filter(r -> r.getStatus() == TransactionRequestStatus.COMPLETED)
                .mapToInt(r -> financialTransactions(r).size()).sum());
    }

    @Test
    void concurrentTransfersCannotOverdrawSourceAccount() throws Exception {
        // Default fee config: 0.1% with a 500 minimum -> each 60,000 transfer costs 60,500 in total,
        // so a 100,000 balance can fund exactly ONE of the two concurrent transfers.
        Account source = newCustomerAccount(new BigDecimal("100000.0000"));
        Account destination = newCustomerAccount(BigDecimal.ZERO);
        BigDecimal bankBefore = accountRepository.findByAccountNumber(systemAccountNumber)
                .orElseThrow().getBalance();

        TransactionRequest first = submit("TRANSFER", TransactionType.TRANSFER,
                source.getAccountNumber(), destination.getAccountNumber(), "60000.0000");
        TransactionRequest second = submit("TRANSFER", TransactionType.TRANSFER,
                source.getAccountNumber(), destination.getAccountNumber(), "60000.0000");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var f1 = executor.submit(() -> { start.await(); processor.processRequest(first.getId()); return null; });
            var f2 = executor.submit(() -> { start.await(); processor.processRequest(second.getId()); return null; });
            start.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        List<TransactionRequest> results = List.of(getRequest(first.getId()), getRequest(second.getId()));
        assertEquals(1, results.stream().filter(r -> r.getStatus() == TransactionRequestStatus.COMPLETED).count());
        assertEquals(1, results.stream().filter(r -> r.getStatus() == TransactionRequestStatus.FAILED).count());
        assertMoney("39500.0000", accountRepository.findById(source.getId()).orElseThrow().getBalance());
        assertMoney("60000.0000", accountRepository.findById(destination.getId()).orElseThrow().getBalance());
        assertMoney(bankBefore.add(new BigDecimal("500.0000")).toPlainString(),
                accountRepository.findByAccountNumber(systemAccountNumber).orElseThrow().getBalance());
    }

    private TransactionRequest submit(String suffix, TransactionType type,
                                     String source, String destination, String amount) {
        return submissionService.submit(new CreateTransactionRequest(
                "oracle-it-" + suffix + "-" + UUID.randomUUID(),
                "key-" + UUID.randomUUID(), type, source, destination,
                new BigDecimal(amount))).request();
    }

    private TransactionRequest getRequest(Long id) {
        return requestRepository.findById(id).orElseThrow();
    }

    private List<FinancialTransaction> financialTransactions(TransactionRequest request) {
        return financialTransactionRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
    }

    private List<LedgerEntry> ledgerEntries(List<FinancialTransaction> transactions) {
        return ledgerEntryRepository.findByFinancialTransactionIdIn(
                transactions.stream().map(FinancialTransaction::getId).toList());
    }

    private Account newCustomerAccount(BigDecimal initialBalance) {
        String identity = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbcTemplate.update(
                "INSERT INTO CUSTOMERS (CUSTOMER_TYPE, IDENTITY_TYPE, IDENTITY_NUMBER, FULL_NAME) VALUES (?, ?, ?, ?)",
                "INDIVIDUAL", "NATIONAL_ID", identity, "Oracle Integration Test");
        Long customerId = jdbcTemplate.queryForObject(
                "SELECT ID FROM CUSTOMERS WHERE IDENTITY_TYPE = ? AND IDENTITY_NUMBER = ?",
                Long.class, "NATIONAL_ID", identity);

        String accountNumber;
        do {
            accountNumber = Long.toString(java.util.concurrent.ThreadLocalRandom.current()
                    .nextLong(10_000_000_000_000L, 100_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber)
                || systemAccountNumber.equals(accountNumber));

        Account account = Account.openCustomerAccount(accountNumber, customerId);
        if (initialBalance.signum() > 0) {
            account.credit(initialBalance);
        }
        return accountRepository.saveAndFlush(account);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, actual.compareTo(new BigDecimal(expected)),
                "Expected balance " + expected + " but was " + actual);
    }
}

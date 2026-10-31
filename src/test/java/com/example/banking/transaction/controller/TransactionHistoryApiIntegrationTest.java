package com.example.banking.transaction.controller;

import com.example.banking.account.entity.Account;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionType;
import com.example.banking.transaction.service.TransactionSubmissionService;
import com.example.banking.transaction.service.TransactionWorker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "banking.worker.poll-interval-ms=3600000"
})
@TestPropertySource(properties = {
        "banking.security.username=banking-admin",
        "banking.security.password=BankingDev2026"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "oracle.integration", matches = "true")
@TestPropertySource(properties = {
        "banking.security.username=test-admin",
        "banking.security.password=test-password"
})
class TransactionHistoryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionSubmissionService submissionService;

    @Autowired
    private TransactionWorker worker;

    @Test
    @Transactional
    void historyEndpointAppliesFiltersAndPagination() throws Exception {
        Account source = newCustomerAccount(new BigDecimal("1000.0000"));
        Account destination = newCustomerAccount(BigDecimal.ZERO);

        TransactionRequest firstWithdrawal = submit(
                "history-withdrawal-one",
                TransactionType.WITHDRAWAL,
                source.getAccountNumber(),
                null,
                "111.1100");

        worker.poll();

        TransactionRequest secondWithdrawal = submit(
                "history-withdrawal-two",
                TransactionType.WITHDRAWAL,
                source.getAccountNumber(),
                null,
                "22.2200");

        worker.poll();

        TransactionRequest deposit = submit(
                "history-deposit",
                TransactionType.DEPOSIT,
                null,
                destination.getAccountNumber(),
                "222.2200");

        worker.poll();

        String auth = authorizationHeader();

        // Account filter: the source account has exactly two withdrawals.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", source.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));

        // Account filter: the destination account has one deposit.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", destination.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)));

        // Type filter scoped to this test's source account.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", source.getAccountNumber())
                        .param("type", "WITHDRAWAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Deposit type filter scoped to this test's destination account.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", destination.getAccountNumber())
                        .param("type", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Source account number filter.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("sourceAccountNumber", source.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // Destination account number filter.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("destinationAccountNumber",
                                destination.getAccountNumber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        // Amount range scoped to the account created by this test.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", destination.getAccountNumber())
                        .param("minAmount", "200")
                        .param("maxAmount", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(222.22));

        // Future lower bound excludes this test's source-account records.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", source.getAccountNumber())
                        .param("from", "2100-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Historical upper bound excludes these records.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", source.getAccountNumber())
                        .param("to", "2000-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Pagination is tested against exactly two records from this test.
        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", source.getAccountNumber())
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/v1/transactions/history")
                        .header("Authorization", auth)
                        .param("accountNumber", source.getAccountNumber())
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    private TransactionRequest submit(
            String label,
            TransactionType type,
            String sourceAccountNumber,
            String destinationAccountNumber,
            String amount) {

        String suffix = UUID.randomUUID().toString();

        return submissionService.submit(new CreateTransactionRequest(
                "history-" + label + "-" + suffix,
                "history-key-" + suffix,
                type,
                sourceAccountNumber,
                destinationAccountNumber,
                new BigDecimal(amount)
        )).request();
    }

    private Account newCustomerAccount(BigDecimal initialBalance) {
        String identity = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 24);

        jdbcTemplate.update(
                """
                INSERT INTO CUSTOMERS
                    (CUSTOMER_TYPE, IDENTITY_TYPE, IDENTITY_NUMBER, FULL_NAME,
                     BIRTH_OR_REG_DATE, MOBILE, ADDRESS, POSTAL_CODE)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "INDIVIDUAL",
                "NATIONAL_ID",
                identity,
                "History API Integration Test",
                Date.valueOf(LocalDate.of(1990, 1, 1)),
                "09120000000",
                "Tehran Integration Test Address",
                "1234567890");

        Long customerId = jdbcTemplate.queryForObject(
                """
                SELECT ID FROM CUSTOMERS
                WHERE IDENTITY_TYPE = ? AND IDENTITY_NUMBER = ?
                """,
                Long.class,
                "NATIONAL_ID",
                identity);

        String accountNumber;

        do {
            accountNumber = Long.toString(
                    ThreadLocalRandom.current().nextLong(
                            10_000_000_000_000L,
                            100_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber)
                || "99999999999999".equals(accountNumber));

        Account account = Account.openCustomerAccount(
                accountNumber, customerId);

        if (initialBalance.signum() > 0) {
            account.credit(initialBalance);
        }

        return accountRepository.saveAndFlush(account);
    }

    private String authorizationHeader() {
        String username = System.getenv().getOrDefault("BANKING_API_USERNAME", "banking-admin");
        String password = System.getenv().getOrDefault("BANKING_API_PASSWORD", "BankingDev2026");

        String credentials = username + ":" + password;

        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void historyEndpointRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(
                get("/api/v1/transactions/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyEndpointRejectsInvalidBasicCredentials() throws Exception {
        String invalidCredentials = "wrong-user:wrong-password";
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString(
                        invalidCredentials.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                get("/api/v1/transactions/history")
                        .header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyEndpointRejectsUnknownTransactionType() throws Exception {
        mockMvc.perform(
                get("/api/v1/transactions/history")
                        .header("Authorization", authorizationHeader())
                        .param("type", "NOT_A_REAL_TRANSACTION_TYPE"))
                .andExpect(status().isBadRequest());
    }
}
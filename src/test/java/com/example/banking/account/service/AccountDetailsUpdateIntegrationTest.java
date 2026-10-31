package com.example.banking.account.service;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.UpdateAccountDetailsRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfSystemProperty(named = "oracle.integration", matches = "true")
@TestPropertySource(properties = {"banking.security.username=test-admin", "banking.security.password=test-password"})
class AccountDetailsUpdateIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccountRepository accountRepository;
    @Autowired private AccountAuditRepository auditRepository;
    @Autowired private AccountDetailsUpdateService updateService;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> customerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long accountId : accountIds) {
            jdbcTemplate.update("DELETE FROM ACCOUNT_AUDIT WHERE ACCOUNT_ID = ?", accountId);
            jdbcTemplate.update("DELETE FROM ACCOUNTS WHERE ID = ?", accountId);
        }
        for (Long customerId : customerIds) {
            jdbcTemplate.update("DELETE FROM CUSTOMERS WHERE ID = ?", customerId);
        }
    }

    @Test
    void updatesProfileAndAuditsOnlyChangedFields() {
        Account account = createAccount("Before Name", "ADDR-OLD");
        String accountNumber = account.getAccountNumber();
        var createdAt = jdbcTemplate.queryForObject(
                "SELECT CREATED_AT FROM ACCOUNTS WHERE ID = ?",
                (rs, rowNum) -> rs.getTimestamp("CREATED_AT").toInstant(),
                account.getId()
        );
        var oldUpdatedAt = account.getUpdatedAt();

        AccountDetailsResponse response = updateService.update(
                account.getId(),
                request("INDIVIDUAL", "NATIONAL_ID", newValidNationalCode(), "After Name",
                        LocalDate.of(2000, 4, 12), "09120000000", "ADDR-NEW", "1111111111"),
                "operator-1");

        assertEquals("After Name", response.fullName());
        assertEquals("ADDR-NEW", response.address());
        assertEquals(accountNumber, response.accountNumber(), "Account number must be immutable.");
        assertEquals(
                createdAt,
                response.accountCreatedAt().toInstant(),
                "Account creation timestamp must be immutable."
        );
        assertEquals(AccountStatus.ACTIVE, response.status());
        assertEquals(0, response.balance().compareTo(BigDecimal.ZERO));
        assertNotNull(response.accountUpdatedAt());
        assertTrue(!response.accountUpdatedAt().isBefore(oldUpdatedAt),
                "Account last-updated time should not move backwards.");

        List<AccountAudit> audits = auditRepository.findByAccountIdOrderByChangedAtDesc(account.getId());
        assertEquals(6, audits.size(), "Exactly six changed fields should be audited.");
        assertTrue(audits.stream().allMatch(a -> a.getChangedBy().equals("operator-1")));
        assertTrue(audits.stream().anyMatch(a -> a.getFieldName().equals("FULL_NAME")
                && a.getOldValue().equals("Before Name") && a.getNewValue().equals("After Name")));
        assertTrue(audits.stream().anyMatch(a -> a.getFieldName().equals("ADDRESS")
                && a.getOldValue().equals("ADDR-OLD") && a.getNewValue().equals("ADDR-NEW")));
    }

    @Test
    void rejectsDuplicateIdentityAndDoesNotChangeProfileOrAudit() {
        Account target = createAccount("Target Customer", "Target address");
        String duplicateIdentity = newValidNationalCode();
        createCustomer("Other Customer", duplicateIdentity, "Other address");

        UpdateAccountDetailsRequest request = request(
                "INDIVIDUAL", "NATIONAL_ID", duplicateIdentity, "Changed Name",
                LocalDate.of(1999, 2, 10), "09120000001", "Changed address", "2222222222");

        assertThrows(ConflictException.class,
                () -> updateService.update(target.getId(), request, "operator-2"));

        String nameInDb = jdbcTemplate.queryForObject(
                "SELECT FULL_NAME FROM CUSTOMERS WHERE ID = ?", String.class, target.getCustomerId());
        assertEquals("Target Customer", nameInDb);
        assertTrue(auditRepository.findByAccountIdOrderByChangedAtDesc(target.getId()).isEmpty());
    }

    @Test
    void rejectsInvalidCustomerTypeWithoutWritingAudit() {
        Account account = createAccount("Stable Name", "Stable address");
        UpdateAccountDetailsRequest request = request(
                "UNKNOWN", "NATIONAL_ID", newValidNationalCode(), "Changed Name",
                LocalDate.of(1999, 2, 10), "09120000001", "Changed address", "2222222222");

        assertThrows(IllegalArgumentException.class,
                () -> updateService.update(account.getId(), request, "operator-3"));

        assertTrue(auditRepository.findByAccountIdOrderByChangedAtDesc(account.getId()).isEmpty());
    }

    private Account createAccount(String fullName, String address) {
        String identityNumber = newValidNationalCode();
        Long customerId = createCustomer(fullName, identityNumber, address);
        String accountNumber;
        do {
            accountNumber = Long.toString(ThreadLocalRandom.current()
                    .nextLong(10_000_000_000_000L, 100_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = accountRepository.saveAndFlush(
                Account.openCustomerAccount(accountNumber, customerId));
        accountIds.add(account.getId());
        return account;
    }

    private Long createCustomer(String fullName, String identityNumber, String address) {
        String identityType = "NATIONAL_ID";
        jdbcTemplate.update("""
                INSERT INTO CUSTOMERS
                  (CUSTOMER_TYPE, IDENTITY_TYPE, IDENTITY_NUMBER, FULL_NAME,
                   BIRTH_OR_REG_DATE, MOBILE, ADDRESS, POSTAL_CODE)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, "INDIVIDUAL", identityType, identityNumber, fullName,
                Date.valueOf(LocalDate.of(1995, 1, 1)), "09121111111", address, "1234567890");
        Long id = jdbcTemplate.queryForObject(
                "SELECT ID FROM CUSTOMERS WHERE IDENTITY_TYPE = ? AND IDENTITY_NUMBER = ?",
                Long.class, identityType, identityNumber);
        customerIds.add(id);
        return id;
    }

    private static UpdateAccountDetailsRequest request(
            String customerType, String identityType, String identityNumber,
            String fullName, LocalDate date, String mobile, String address,
            String postalCode) {
        return new UpdateAccountDetailsRequest(customerType, identityType, identityNumber,
                fullName, date, mobile, address, postalCode);
    }

    /** Generates a unique 10-digit Iranian national code with a valid check digit. */
    private static String newValidNationalCode() {
        while (true) {
            StringBuilder sb = new StringBuilder(10);
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                int digit = ThreadLocalRandom.current().nextInt(10);
                sb.append(digit);
                sum += digit * (10 - i);
            }
            int remainder = sum % 11;
            int check = remainder < 2 ? remainder : 11 - remainder;
            sb.append(check);
            String candidate = sb.toString();
            if (candidate.chars().distinct().count() > 1) {
                return candidate;
            }
        }
    }
}

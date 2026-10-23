package com.example.banking.account.service;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.AccountDetailsResponse.CustomerDetails;
import com.example.banking.account.dto.UpdateAccountDetailsRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ConflictException;
import com.example.banking.common.exception.ResourceNotFoundException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AccountDetailsUpdateService {

    private static final String CUSTOMER_SELECT = """
            SELECT ID, CUSTOMER_TYPE, IDENTITY_TYPE, IDENTITY_NUMBER,
                   FULL_NAME, BIRTH_OR_REG_DATE, MOBILE, ADDRESS, POSTAL_CODE
            FROM CUSTOMERS
            WHERE ID = ?
            """;

    private final AccountRepository accountRepository;
    private final AccountAuditRepository auditRepository;
    private final JdbcTemplate jdbcTemplate;

    public AccountDetailsUpdateService(
            AccountRepository accountRepository,
            AccountAuditRepository auditRepository,
            JdbcTemplate jdbcTemplate) {
        this.accountRepository = accountRepository;
        this.auditRepository = auditRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Updates customer registration fields linked to the account.
     * Account number and account creation timestamp are intentionally immutable.
     * Customer changes, account last-updated timestamp and audit rows share one DB transaction.
     */
    @Transactional
    public AccountDetailsResponse update(Long accountId, UpdateAccountDetailsRequest request) {
        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Invalid account ID.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));

        if (account.getAccountRole() != AccountRole.CUSTOMER || account.getCustomerId() == null) {
            throw new IllegalArgumentException("Customer details cannot be changed for a system account.");
        }

        NormalizedRequest normalized = normalizeAndValidate(request);
        CustomerDetails old = loadCustomer(account.getCustomerId());

        long duplicates = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM CUSTOMERS
                WHERE IDENTITY_TYPE = ? AND IDENTITY_NUMBER = ? AND ID <> ?
                """, Long.class,
                normalized.identityType(), normalized.identityNumber(), old.id());
        if (duplicates > 0) {
            throw new ConflictException("A customer with this identity type and number already exists.");
        }

        List<AccountAudit> audits = new ArrayList<>();
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "CUSTOMER_TYPE",
                old.customerType(), normalized.customerType());
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "IDENTITY_TYPE",
                old.identityType(), normalized.identityType());
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "IDENTITY_NUMBER",
                old.identityNumber(), normalized.identityNumber());
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "FULL_NAME",
                old.fullName(), normalized.fullName());
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "BIRTH_OR_REG_DATE",
                asString(old.birthOrRegDate()), asString(normalized.birthOrRegDate()));
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "MOBILE",
                old.mobile(), normalized.mobile());
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "ADDRESS",
                old.address(), normalized.address());
        addAuditIfChanged(audits, accountId, normalized.changedBy(), "POSTAL_CODE",
                old.postalCode(), normalized.postalCode());

        if (!audits.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE CUSTOMERS
                    SET CUSTOMER_TYPE = ?, IDENTITY_TYPE = ?, IDENTITY_NUMBER = ?,
                        FULL_NAME = ?, BIRTH_OR_REG_DATE = ?, MOBILE = ?, ADDRESS = ?, POSTAL_CODE = ?
                    WHERE ID = ?
                    """,
                    normalized.customerType(),
                    normalized.identityType(),
                    normalized.identityNumber(),
                    normalized.fullName(),
                    Date.valueOf(normalized.birthOrRegDate()),
                    normalized.mobile(),
                    normalized.address(),
                    normalized.postalCode(),
                    old.id());

            // Customer fields are part of the customer profile shown with the account;
            // their modification must refresh the account's last-updated timestamp too.
            jdbcTemplate.update("UPDATE ACCOUNTS SET UPDATED_AT = SYSTIMESTAMP WHERE ID = ?", accountId);
            auditRepository.saveAll(audits);
        }

        CustomerDetails updated = loadCustomer(account.getCustomerId());
        OffsetDateTime updatedAt = loadAccountUpdatedAt(accountId);
        return AccountDetailsResponse.of(account, updated, updatedAt);
    }

    private CustomerDetails loadCustomer(Long customerId) {
        try {
            return jdbcTemplate.queryForObject(CUSTOMER_SELECT, (rs, rowNum) -> {
                Date date = rs.getDate("BIRTH_OR_REG_DATE");
                return new CustomerDetails(
                        rs.getLong("ID"),
                        rs.getString("CUSTOMER_TYPE"),
                        rs.getString("IDENTITY_TYPE"),
                        rs.getString("IDENTITY_NUMBER"),
                        rs.getString("FULL_NAME"),
                        date == null ? null : date.toLocalDate(),
                        rs.getString("MOBILE"),
                        rs.getString("ADDRESS"),
                        rs.getString("POSTAL_CODE")
                );
            }, customerId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Customer not found.");
        }
    }

    private OffsetDateTime loadAccountUpdatedAt(Long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT UPDATED_AT FROM ACCOUNTS WHERE ID = ?",
                (rs, rowNum) -> rs.getObject(1, OffsetDateTime.class),
                accountId);
    }

    private static void addAuditIfChanged(
            List<AccountAudit> target,
            Long accountId,
            String changedBy,
            String field,
            String oldValue,
            String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            target.add(new AccountAudit(accountId, changedBy, field, oldValue, newValue));
        }
    }

    private static String asString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static NormalizedRequest normalizeAndValidate(UpdateAccountDetailsRequest request) {
        String customerType = required(request.customerType(), "Customer type", 20)
                .toUpperCase(Locale.ROOT);
        if (!List.of("INDIVIDUAL", "CORPORATE", "FOREIGN").contains(customerType)) {
            throw new IllegalArgumentException("Customer type must be INDIVIDUAL, CORPORATE or FOREIGN.");
        }

        String identityType = required(request.identityType(), "Identity type", 30)
                .toUpperCase(Locale.ROOT);
        String identityNumber = required(request.identityNumber(), "Identity number", 50);
        String fullName = required(request.fullName(), "Full name", 200);
        LocalDate birthOrRegDate = request.birthOrRegDate();
        if (birthOrRegDate == null) {
            throw new IllegalArgumentException("Birth or registration date is required.");
        }
        if (birthOrRegDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Birth or registration date cannot be in the future.");
        }
        String mobile = required(request.mobile(), "Mobile", 20);
        String address = required(request.address(), "Address", 500);
        String postalCode = required(request.postalCode(), "Postal code", 20);
        String changedBy = required(request.changedBy(), "The actor making the change", 100);

        return new NormalizedRequest(customerType, identityType, identityNumber,
                fullName, birthOrRegDate, mobile, address, postalCode, changedBy);
    }

    private static String required(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    private record NormalizedRequest(
            String customerType,
            String identityType,
            String identityNumber,
            String fullName,
            LocalDate birthOrRegDate,
            String mobile,
            String address,
            String postalCode,
            String changedBy) {
    }
}

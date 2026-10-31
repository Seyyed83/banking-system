package com.example.banking.account.dto;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.customer.entity.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Account data and its associated customer registration details. */
public record AccountDetailsResponse(
        Long accountId,
        String accountNumber,
        AccountStatus status,
        BigDecimal balance,
        OffsetDateTime accountCreatedAt,
        OffsetDateTime accountUpdatedAt,
        Long customerId,
        String customerType,
        String identityType,
        String identityNumber,
        String fullName,
        LocalDate birthOrRegDate,
        String mobile,
        String address,
        String postalCode
) {
    public static AccountDetailsResponse of(
            Account account,
            CustomerDetails customer,
            OffsetDateTime accountUpdatedAt) {
        return new AccountDetailsResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedAt(),
                accountUpdatedAt,
                customer.id(),
                customer.customerType(),
                customer.identityType(),
                customer.identityNumber(),
                customer.fullName(),
                customer.birthOrRegDate(),
                customer.mobile(),
                customer.address(),
                customer.postalCode()
        );
    }

    public static AccountDetailsResponse of(
            Account account,
            Customer customer) {
        return new AccountDetailsResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                customer.getId(),
                customer.getCustomerType(),
                customer.getIdentityType(),
                customer.getIdentityNumber(),
                customer.getFullName(),
                customer.getBirthOrRegDate(),
                customer.getMobile(),
                customer.getAddress(),
                customer.getPostalCode()
        );
    }

    /** Internal projection is public so the service can map JDBC results cleanly. */
    public record CustomerDetails(
            Long id,
            String customerType,
            String identityType,
            String identityNumber,
            String fullName,
            LocalDate birthOrRegDate,
            String mobile,
            String address,
            String postalCode
    ) {
    }
}

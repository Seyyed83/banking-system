package com.example.banking.account.dto;

import java.time.LocalDate;

/** Full replacement of the customer details associated with an account. */
public record UpdateAccountDetailsRequest(
        String customerType,
        String identityType,
        String identityNumber,
        String fullName,
        LocalDate birthOrRegDate,
        String mobile,
        String address,
        String postalCode,
        String changedBy
) {
}

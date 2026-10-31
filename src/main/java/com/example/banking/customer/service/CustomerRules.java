package com.example.banking.customer.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class CustomerRules {

    public static final List<String> TYPES =
            List.of("INDIVIDUAL", "CORPORATE", "FOREIGN");

    public static final List<String> IDENTITY_TYPES =
            List.of("NATIONAL_ID", "LEGAL_ID", "FOREIGN_ID", "PASSPORT");

    private CustomerRules() {}

    public static void validate(
            String customerType,
            String identityNumber,
            LocalDate birthOrRegDate,
            String mobile,
            String postalCode
    ) {
        validate(customerType, null, identityNumber, birthOrRegDate, mobile, postalCode);
    }

    public static void validate(
            String customerType,
            String identityType,
            String identityNumber,
            LocalDate birthOrRegDate,
            String mobile,
            String postalCode
    ) {
        if (customerType == null ||
                !TYPES.contains(customerType.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Customer type must be INDIVIDUAL, CORPORATE or FOREIGN."
            );
        }

        String normalizedCustomerType = customerType.trim().toUpperCase(Locale.ROOT);
        String normalizedIdentityType = identityType == null || identityType.isBlank()
                ? defaultIdentityTypeFor(normalizedCustomerType)
                : identityType.trim().toUpperCase(Locale.ROOT);

        if (!IDENTITY_TYPES.contains(normalizedIdentityType)) {
            throw new IllegalArgumentException(
                    "Identity type must be NATIONAL_ID, LEGAL_ID, FOREIGN_ID or PASSPORT."
            );
        }

        String id = identityNumber == null ? "" : identityNumber.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Identity number is required.");
        }

        switch (normalizedIdentityType) {
            case "NATIONAL_ID" -> {
                if (!isValidNationalCode(id)) {
                    throw new IllegalArgumentException(
                            "National code is invalid (must be a valid 10-digit Iranian national code)."
                    );
                }
            }
            case "LEGAL_ID" -> {
                if (!id.matches("\\d{11}")) {
                    throw new IllegalArgumentException(
                            "Legal entity national ID must be 11 digits."
                    );
                }
            }
            case "FOREIGN_ID", "PASSPORT" -> {
                if (!id.matches("[0-9A-Za-z]{5,50}")) {
                    throw new IllegalArgumentException(
                            "Foreign national ID or passport must be 5 to 50 alphanumeric characters."
                    );
                }
            }
        }

        if (birthOrRegDate == null ||
                birthOrRegDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Birth/registration date is required and cannot be in the future."
            );
        }

        if (mobile == null ||
                !mobile.trim().matches("09\\d{9}")) {
            throw new IllegalArgumentException(
                    "Mobile number must be 11 digits and start with 09."
            );
        }

        if (postalCode == null ||
                !postalCode.trim().matches("\\d{10}")) {
            throw new IllegalArgumentException(
                    "Postal code must be 10 digits."
            );
        }
    }

    private static String defaultIdentityTypeFor(String customerType) {
        return switch (customerType) {
            case "INDIVIDUAL" -> "NATIONAL_ID";
            case "CORPORATE" -> "LEGAL_ID";
            case "FOREIGN" -> "FOREIGN_ID";
            default -> "NATIONAL_ID";
        };
    }

    /**
     * Iranian national code check-digit algorithm.
     */
    public static boolean isValidNationalCode(String code) {
        if (code == null || !code.matches("\\d{10}")) {
            return false;
        }

        if (code.chars().distinct().count() == 1) {
            return false;
        }

        int sum = 0;

        for (int i = 0; i < 9; i++) {
            sum += (code.charAt(i) - '0') * (10 - i);
        }

        int remainder = sum % 11;
        int check = code.charAt(9) - '0';

        return remainder < 2
                ? check == remainder
                : check == 11 - remainder;
    }
}

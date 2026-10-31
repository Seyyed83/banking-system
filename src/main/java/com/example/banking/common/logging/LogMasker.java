package com.example.banking.common.logging;

/**
 * Utility for masking sensitive financial identifiers (such as 14-digit account numbers)
 * before writing them to application logs.
 */
public final class LogMasker {

    private LogMasker() {}

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "N/A";
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4)
                + "******"
                + trimmed.substring(trimmed.length() - 4);
    }

    public static String maskIdentity(String identityNumber) {
        if (identityNumber == null || identityNumber.isBlank()) {
            return "N/A";
        }
        String trimmed = identityNumber.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2)
                + "****"
                + trimmed.substring(trimmed.length() - 2);
    }
}

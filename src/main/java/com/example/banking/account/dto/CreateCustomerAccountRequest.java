package com.example.banking.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request payload for unified customer registration and account creation (Task section 2-1).
 */
public record CreateCustomerAccountRequest(
        @NotBlank(message = "Customer type is required (INDIVIDUAL, CORPORATE, or FOREIGN).")
        @Size(max = 20, message = "Customer type cannot exceed 20 characters.")
        String customerType,

        @NotBlank(message = "Identity type is required (NATIONAL_ID, LEGAL_ID, FOREIGN_ID, or PASSPORT).")
        @Size(max = 30, message = "Identity type cannot exceed 30 characters.")
        String identityType,

        @NotBlank(message = "Identity number is required.")
        @Size(max = 50, message = "Identity number cannot exceed 50 characters.")
        String identityNumber,

        @NotBlank(message = "Full name is required.")
        @Size(max = 200, message = "Full name cannot exceed 200 characters.")
        String fullName,

        @NotNull(message = "Birth or registration date is required.")
        @PastOrPresent(message = "Birth or registration date cannot be in the future.")
        LocalDate birthOrRegDate,

        @NotBlank(message = "Mobile number is required.")
        @Pattern(regexp = "09\\d{9}", message = "Mobile number must be 11 digits and start with 09.")
        String mobile,

        @NotBlank(message = "Address is required.")
        @Size(max = 500, message = "Address cannot exceed 500 characters.")
        String address,

        @NotBlank(message = "Postal code is required.")
        @Pattern(regexp = "\\d{10}", message = "Postal code must be 10 digits.")
        String postalCode
) {
}

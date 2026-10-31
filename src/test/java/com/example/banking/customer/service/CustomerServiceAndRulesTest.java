package com.example.banking.customer.service;

import com.example.banking.common.exception.ConflictException;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceAndRulesTest {

    @Mock
    private CustomerRepository customerRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
    }

    @Test
    void validatesIndividualWithValidIranianNationalCode() {
        assertDoesNotThrow(() -> CustomerRules.validate(
                "INDIVIDUAL",
                "NATIONAL_ID",
                "0010350829",
                LocalDate.of(1990, 5, 15),
                "09123456789",
                "1234567890"
        ));
    }

    @Test
    void rejectsNationalCodeWithInvalidCheckDigit() {
        assertThrows(IllegalArgumentException.class, () -> CustomerRules.validate(
                "INDIVIDUAL",
                "NATIONAL_ID",
                "0010350828",
                LocalDate.of(1990, 5, 15),
                "09123456789",
                "1234567890"
        ));
    }

    @Test
    void validatesCorporateWith11DigitLegalId() {
        assertDoesNotThrow(() -> CustomerRules.validate(
                "CORPORATE",
                "LEGAL_ID",
                "10101234567",
                LocalDate.of(2010, 1, 1),
                "09123456789",
                "1234567890"
        ));
    }

    @Test
    void validatesForeignCustomerWithAlphanumericId() {
        assertDoesNotThrow(() -> CustomerRules.validate(
                "FOREIGN",
                "FOREIGN_ID",
                "FN9988776655",
                LocalDate.of(1988, 8, 8),
                "09123456789",
                "1234567890"
        ));
    }

    @Test
    void rejectsFutureBirthDate() {
        assertThrows(IllegalArgumentException.class, () -> CustomerRules.validate(
                "INDIVIDUAL",
                "NATIONAL_ID",
                "0010350829",
                LocalDate.now().plusDays(1),
                "09123456789",
                "1234567890"
        ));
    }

    @Test
    void rejectsDuplicateIdentityRegistration() {
        when(customerRepository.existsByIdentityTypeAndIdentityNumber("NATIONAL_ID", "0010350829"))
                .thenReturn(true);

        Customer customer = new Customer(
                "INDIVIDUAL",
                "NATIONAL_ID",
                "0010350829",
                "Ali Rezaei",
                LocalDate.of(1990, 5, 15),
                "09123456789",
                "Tehran, Valiasr St.",
                "1234567890"
        );

        assertThrows(ConflictException.class, () -> customerService.register(customer));
        verify(customerRepository, never()).saveAndFlush(any());
    }
}

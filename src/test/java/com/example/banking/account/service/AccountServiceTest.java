package com.example.banking.account.service;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.CreateCustomerAccountRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ConflictException;
import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import com.example.banking.customer.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerRepository customerRepository;

    private CustomerService customerService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository);
        accountService = new AccountService(accountRepository, customerRepository, customerService);
    }

    @Test
    void createsAccountForExistingCustomer() {
        when(customerRepository.existsById(42L))
                .thenReturn(true);

        when(accountRepository.existsByCustomerId(42L))
                .thenReturn(false);

        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(false);

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account account = accountService.createAccount(42L);

        assertNotNull(account);
        assertEquals(Long.valueOf(42L), account.getCustomerId());
        assertEquals(AccountRole.CUSTOMER, account.getAccountRole());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());

        assertTrue(
                account.getAccountNumber().matches("[0-9]{14}"),
                "Account number must contain exactly 14 digits."
        );

        assertEquals(
                0,
                account.getBalance().compareTo(BigDecimal.ZERO)
        );

        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void rejectsSecondAccountForCustomerWhoAlreadyHasAccount() {
        when(customerRepository.existsById(42L))
                .thenReturn(true);

        when(accountRepository.existsByCustomerId(42L))
                .thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> accountService.createAccount(42L)
        );

        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void createsCustomerAndAccountInOneUnifiedOperation() {
        CreateCustomerAccountRequest request = new CreateCustomerAccountRequest(
                "INDIVIDUAL",
                "NATIONAL_ID",
                "0010350829",
                "Ali Rezaei",
                LocalDate.of(1995, 5, 10),
                "09123456789",
                "Tehran, Valiasr St.",
                "1234567890"
        );

        when(customerRepository.existsByIdentityTypeAndIdentityNumber("NATIONAL_ID", "0010350829"))
                .thenReturn(false);

        when(customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> {
                    Customer saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 101L);
                    return saved;
                });

        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(false);

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> {
                    Account saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 202L);
                    return saved;
                });

        AccountDetailsResponse response = accountService.createCustomerAndAccount(request);

        assertNotNull(response);
        assertEquals(Long.valueOf(202L), response.accountId());
        assertEquals(Long.valueOf(101L), response.customerId());
        assertTrue(response.accountNumber().matches("[0-9]{14}"));
        assertEquals(AccountStatus.ACTIVE, response.status());
        assertEquals(0, response.balance().compareTo(BigDecimal.ZERO));
        assertEquals("Ali Rezaei", response.fullName());
        assertEquals("0010350829", response.identityNumber());
    }

    @Test
    void rejectsAccountCreationForUnknownCustomer() {
        when(customerRepository.existsById(42L)).thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.createAccount(42L)
        );

        verifyNoInteractions(accountRepository);
    }

    @Test
    void retriesWhenGeneratedAccountNumberAlreadyExists() {
        when(customerRepository.existsById(42L))
                .thenReturn(true);

        when(accountRepository.existsByCustomerId(42L))
                .thenReturn(false);

        when(accountRepository.existsByAccountNumber(anyString()))
                .thenReturn(true, false);

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account account = accountService.createAccount(42L);

        assertTrue(
                account.getAccountNumber().matches("[0-9]{14}")
        );

        verify(accountRepository, times(2))
                .existsByAccountNumber(anyString());

        verify(accountRepository).saveAndFlush(any(Account.class));
    }
}
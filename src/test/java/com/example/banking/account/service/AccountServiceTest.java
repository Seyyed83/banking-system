package com.example.banking.account.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.banking.common.exception.ResourceNotFoundException;

import java.math.BigDecimal;

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

    @InjectMocks
    private AccountService accountService;

    @Test
    void createsAccountForExistingCustomer() {
        when(customerRepository.existsById(42L))
                .thenReturn(true);

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
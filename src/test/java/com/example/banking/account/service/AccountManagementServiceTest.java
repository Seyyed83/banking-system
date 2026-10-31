package com.example.banking.account.service;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountAuditRepository auditRepository;

    @Mock
    private CustomerRepository customerRepository;

    private AccountManagementService service;

    @BeforeEach
    void setUp() {
        service = new AccountManagementService(
                accountRepository,
                auditRepository,
                customerRepository);
    }

    private Account createAccount() {
        Account account = Account.openCustomerAccount(
                "12345678901234", 42L);

        // Simulate an account already persisted in the database.
        ReflectionTestUtils.setField(account, "id", 1L);

        return account;
    }

    private Customer createCustomer() {
        Customer customer = new Customer(
                "INDIVIDUAL",
                "NATIONAL_ID",
                "0010350829",
                "Sara Ahmadi",
                LocalDate.of(1992, 3, 15),
                "09121112233",
                "Tehran, Vanak Sq.",
                "1999999999"
        );
        ReflectionTestUtils.setField(customer, "id", 42L);
        return customer;
    }

    @Test
    void getsAccountById() {
        Account account = createAccount();

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        assertSame(account, service.getById(1L));
    }

    @Test
    void getsFullAccountDetailsByAccountNumber() {
        Account account = createAccount();
        Customer customer = createCustomer();

        when(accountRepository.findByAccountNumber("12345678901234"))
                .thenReturn(Optional.of(account));
        when(customerRepository.findById(42L))
                .thenReturn(Optional.of(customer));

        AccountDetailsResponse details =
                service.getDetailsByAccountNumber("12345678901234");

        assertEquals("12345678901234", details.accountNumber());
        assertEquals("Sara Ahmadi", details.fullName());
        assertEquals("0010350829", details.identityNumber());
        assertEquals(AccountStatus.ACTIVE, details.status());
    }

    @Test
    void getsAccountByCustomerIdentityNumber() {
        Account account = createAccount();
        Customer customer = createCustomer();

        when(customerRepository.findFirstByIdentityNumberOrderByIdAsc("0010350829"))
                .thenReturn(Optional.of(customer));
        when(accountRepository.findFirstByCustomerIdOrderByIdAsc(42L))
                .thenReturn(Optional.of(account));

        AccountDetailsResponse details =
                service.getAccountByCustomerIdentity("0010350829", null);

        assertEquals("12345678901234", details.accountNumber());
        assertEquals(Long.valueOf(42L), details.customerId());
        assertEquals("0010350829", details.identityNumber());
    }

    @Test
    void rejectsUnknownAccount() {
        when(accountRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getById(999L)
        );
    }

    @Test
    void rejectsInvalidAccountNumber() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getByAccountNumber("123"));
    }

    @Test
    void blocksAccountAndRecordsAudit() {
        Account account = createAccount();

        when(accountRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account result = service.changeStatus(
                1L, AccountStatus.BLOCKED, "admin");

        assertEquals(AccountStatus.BLOCKED, result.getStatus());

        ArgumentCaptor<AccountAudit> captor =
                ArgumentCaptor.forClass(AccountAudit.class);

        verify(auditRepository).save(captor.capture());

        AccountAudit audit = captor.getValue();

        assertEquals(1L, audit.getAccountId());
        assertEquals("admin", audit.getChangedBy());
        assertEquals("STATUS", audit.getFieldName());
        assertEquals("ACTIVE", audit.getOldValue());
        assertEquals("BLOCKED", audit.getNewValue());
    }

    @Test
    void doesNotCreateAuditWhenStatusIsUnchanged() {
        Account account = createAccount();

        when(accountRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(account));

        Account result = service.changeStatus(
                1L, AccountStatus.ACTIVE, "admin");

        assertEquals(AccountStatus.ACTIVE, result.getStatus());
        verifyNoInteractions(auditRepository);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void rejectsMissingActor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.changeStatus(
                        1L, AccountStatus.BLOCKED, " "));
    }

    @Test
    void rejectsMissingStatus() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.changeStatus(1L, null, "admin"));
    }
}
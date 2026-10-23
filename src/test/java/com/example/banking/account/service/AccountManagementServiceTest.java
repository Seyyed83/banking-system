package com.example.banking.account.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import com.example.banking.common.exception.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountManagementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountAuditRepository auditRepository;

    @InjectMocks
    private AccountManagementService service;

    private Account createAccount() {
        Account account = Account.openCustomerAccount(
                "12345678901234", 42L);

        // Simulate an account already persisted in the database.
        ReflectionTestUtils.setField(account, "id", 1L);

        return account;
    }

    @Test
    void getsAccountById() {
        Account account = createAccount();

        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(account));

        assertSame(account, service.getById(1L));
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
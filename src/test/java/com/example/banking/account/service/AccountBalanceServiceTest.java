package com.example.banking.account.service;

import com.example.banking.account.cache.AccountBalanceCache;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountBalanceServiceTest {

    private static final String ACCOUNT = "12345678901234";

    private final AccountManagementService management = mock(AccountManagementService.class);
    private final AccountBalanceCache cache = mock(AccountBalanceCache.class);
    private final AccountBalanceService service = new AccountBalanceService(management, cache);

    private Account account() {
        Account account = Account.openCustomerAccount(ACCOUNT, 42L);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("250.0000"));
        return account;
    }

    @Test
    void cacheHitSkipsDatabase() {
        when(cache.get(ACCOUNT)).thenReturn(Optional.of(
                new AccountBalanceCache.CachedBalance(AccountStatus.ACTIVE, new BigDecimal("99.0000"))));

        AccountBalanceView view = service.getBalance(ACCOUNT);

        assertEquals(new BigDecimal("99.0000"), view.balance());
        assertEquals(AccountStatus.ACTIVE, view.status());
        verifyNoInteractions(management);
        verify(cache, never()).put(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cacheMissReadsDatabaseAndPopulatesCache() {
        when(cache.get(ACCOUNT)).thenReturn(Optional.empty());
        Account account = account();
        when(management.getByAccountNumber(ACCOUNT)).thenReturn(account);

        AccountBalanceView view = service.getBalance(ACCOUNT);

        assertEquals(ACCOUNT, view.accountNumber());
        assertEquals(account.getBalance(), view.balance());
        verify(cache).put(ACCOUNT, account.getStatus(), account.getBalance());
    }

    @Test
    void invalidAccountNumberIsRejectedBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.getBalance("abc"));
        assertThrows(IllegalArgumentException.class, () -> service.getBalance(null));
        verifyNoInteractions(cache, management);
    }
}

package com.example.banking.account.service;

import com.example.banking.account.cache.AccountBalanceCache;
import com.example.banking.account.entity.Account;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Balance lookup: Redis first, Oracle as the source of truth on a miss or on any Redis failure. */
@Service
public class AccountBalanceService {

    private final AccountManagementService managementService;
    private final AccountBalanceCache cache;

    public AccountBalanceService(AccountManagementService managementService, AccountBalanceCache cache) {
        this.managementService = managementService;
        this.cache = cache;
    }

    public AccountBalanceView getBalance(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("[0-9]{14}")) {
            throw new IllegalArgumentException("Account number must contain exactly 14 digits.");
        }

        Optional<AccountBalanceCache.CachedBalance> cached = cache.get(accountNumber);
        if (cached.isPresent()) {
            return new AccountBalanceView(accountNumber, cached.get().status(), cached.get().balance());
        }

        Account account = managementService.getByAccountNumber(accountNumber);
        cache.put(account.getAccountNumber(), account.getStatus(), account.getBalance());
        return new AccountBalanceView(
                account.getAccountNumber(), account.getStatus(), account.getBalance());
    }
}

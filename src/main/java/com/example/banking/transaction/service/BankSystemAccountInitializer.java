package com.example.banking.transaction.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BankSystemAccountInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;

    @Value("${banking.system-account-number:99999999999999}")
    private String systemAccountNumber;

    public BankSystemAccountInitializer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (systemAccountNumber == null || !systemAccountNumber.matches("[0-9]{14}")) {
            throw new IllegalStateException("banking.system-account-number must contain exactly 14 digits.");
        }
        var existing = accountRepository.findByAccountNumber(systemAccountNumber);
        if (existing.isPresent()) {
            if (existing.get().getAccountRole() != AccountRole.SYSTEM) {
                throw new IllegalStateException("Configured bank account number is already used by a customer account.");
            }
            return;
        }
        accountRepository.saveAndFlush(Account.openSystemAccount(systemAccountNumber));
    }
}

package com.example.banking.account.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.banking.common.exception.ResourceNotFoundException;

import java.security.SecureRandom;

@Service
public class AccountService {

    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 20;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Account createAccount(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("A valid customer ID is required.");
        }

        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found.");
        }

        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.openCustomerAccount(
                accountNumber,
                customerId
        );

        return accountRepository.saveAndFlush(account);
    }

    private String generateUniqueAccountNumber() {
        for (
                int attempt = 0;
                attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS;
                attempt++
        ) {
            String candidate = generateAccountNumber();

            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not generate a unique account number."
        );
    }

    private String generateAccountNumber() {
        StringBuilder number = new StringBuilder(14);

        number.append(1 + secureRandom.nextInt(9));

        for (int i = 1; i < 14; i++) {
            number.append(secureRandom.nextInt(10));
        }

        return number.toString();
    }
}
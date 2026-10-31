package com.example.banking.account.service;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.CreateCustomerAccountRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ConflictException;
import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.common.logging.LogMasker;
import com.example.banking.customer.entity.Customer;
import com.example.banking.customer.repository.CustomerRepository;
import com.example.banking.customer.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class AccountService {

    private static final Logger log =
            LoggerFactory.getLogger(AccountService.class);

    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 20;

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository
    ) {
        this(accountRepository, customerRepository, new CustomerService(customerRepository));
    }

    @Autowired
    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            CustomerService customerService
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
    }

    /**
     * Unified customer registration and account creation in a single atomic transaction (Task section 2-1).
     */
    @Transactional
    public AccountDetailsResponse createCustomerAndAccount(CreateCustomerAccountRequest request) {
        Customer customer = customerService.register(request);
        Account account = createAccountForVerifiedCustomer(customer.getId());
        return AccountDetailsResponse.of(account, customer);
    }

    @Transactional
    public Account createAccount(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new IllegalArgumentException("A valid customer ID is required.");
        }

        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found.");
        }

        if (accountRepository.existsByCustomerId(customerId)) {
            log.warn("Rejected duplicate account creation attempt for customerId={}", customerId);
            throw new ConflictException("This customer already has an account.");
        }

        return createAccountForVerifiedCustomer(customerId);
    }

    private Account createAccountForVerifiedCustomer(Long customerId) {
        String accountNumber = generateUniqueAccountNumber();

        Account account = Account.openCustomerAccount(
                accountNumber,
                customerId
        );

        Account saved = accountRepository.saveAndFlush(account);
        log.info("Created customer account accountId={}, customerId={}, accountNumber={}, status={}",
                saved.getId(),
                saved.getCustomerId(),
                LogMasker.maskAccountNumber(saved.getAccountNumber()),
                saved.getStatus());
        return saved;
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
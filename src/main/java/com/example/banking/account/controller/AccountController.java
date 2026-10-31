package com.example.banking.account.controller;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.AccountResponse;
import com.example.banking.account.dto.CreateCustomerAccountRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Unified endpoint to register a new customer and open their 14-digit ACTIVE account
     * in a single atomic operation (Task section 2-1).
     */
    @PostMapping("/accounts")
    public ResponseEntity<AccountDetailsResponse> createCustomerAccount(
            @Valid @RequestBody CreateCustomerAccountRequest request) {

        AccountDetailsResponse response =
                accountService.createCustomerAndAccount(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/customers/{customerId}/accounts")
    public ResponseEntity<AccountResponse> createAccount(
            @PathVariable Long customerId) {

        Account account = accountService.createAccount(customerId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(AccountResponse.from(account));
    }
}
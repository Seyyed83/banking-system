package com.example.banking.account.controller;

import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.UpdateAccountDetailsRequest;
import com.example.banking.account.service.AccountDetailsUpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountDetailsController {

    private final AccountDetailsUpdateService updateService;

    public AccountDetailsController(AccountDetailsUpdateService updateService) {
        this.updateService = updateService;
    }

    /**
     * Replaces the registration details of the customer associated with the account.
     * Account number, account creation timestamp and balance cannot be edited here.
     */
    @PutMapping("/{id}/details")
    public ResponseEntity<AccountDetailsResponse> updateCustomerDetails(
            @PathVariable Long id,
            @RequestBody UpdateAccountDetailsRequest request) {
        return ResponseEntity.ok(updateService.update(id, request));
    }
}

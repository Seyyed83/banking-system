package com.example.banking.account.controller;

import com.example.banking.account.dto.AccountAuditResponse;
import com.example.banking.account.dto.AccountResponse;
import com.example.banking.account.dto.ChangeAccountStatusRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountAudit;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.service.AccountManagementService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountManagementController {

    private final AccountManagementService managementService;
    private final AccountAuditRepository auditRepository;

    public AccountManagementController(
            AccountManagementService managementService,
            AccountAuditRepository auditRepository) {
        this.managementService = managementService;
        this.auditRepository = auditRepository;
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                AccountResponse.from(managementService.getById(id)));
    }

    @GetMapping("/accounts/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccountByNumber(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(
                AccountResponse.from(
                        managementService.getByAccountNumber(accountNumber)));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<Page<AccountResponse>> getCustomerAccounts(
            @PathVariable Long customerId,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<AccountResponse> response =
                managementService.getCustomerAccounts(
                        customerId, status, page, size
                ).map(AccountResponse::from);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/accounts/{id}/status")
    public ResponseEntity<AccountResponse> changeAccountStatus(
            @PathVariable Long id,
            @RequestBody ChangeAccountStatusRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        Account updated = managementService.changeStatus(
                id,
                request.status(),
                request.changedBy()
        );

        return ResponseEntity.ok(AccountResponse.from(updated));
    }

    @GetMapping("/accounts/{id}/audit")
    public ResponseEntity<List<AccountAuditResponse>> getAccountAudit(
            @PathVariable Long id) {

        managementService.getById(id);

        List<AccountAuditResponse> response = auditRepository
                .findByAccountIdOrderByChangedAtDesc(id)
                .stream()
                .map(AccountAuditResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }
}
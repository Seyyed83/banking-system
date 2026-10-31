package com.example.banking.account.controller;

import com.example.banking.account.dto.AccountAuditResponse;
import com.example.banking.account.dto.AccountDetailsResponse;
import com.example.banking.account.dto.AccountResponse;
import com.example.banking.account.dto.ChangeAccountStatusRequest;
import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountStatus;
import com.example.banking.account.repository.AccountAuditRepository;
import com.example.banking.account.service.AccountBalanceService;
import com.example.banking.account.service.AccountBalanceView;
import com.example.banking.account.service.AccountManagementService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AccountManagementController {

    private final AccountManagementService managementService;
    private final AccountAuditRepository auditRepository;
    private final AccountBalanceService balanceService;

    public AccountManagementController(
            AccountManagementService managementService,
            AccountAuditRepository auditRepository,
            AccountBalanceService balanceService) {
        this.managementService = managementService;
        this.auditRepository = auditRepository;
        this.balanceService = balanceService;
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                AccountResponse.from(managementService.getById(id)));
    }

    @GetMapping("/accounts/{id}/details")
    public ResponseEntity<AccountDetailsResponse> getAccountDetailsById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                managementService.getDetailsById(id));
    }

    @GetMapping("/accounts/number/{accountNumber}")
    public ResponseEntity<AccountDetailsResponse> getAccountByNumber(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(
                managementService.getDetailsByAccountNumber(accountNumber));
    }

    @GetMapping("/accounts/number/{accountNumber}/details")
    public ResponseEntity<AccountDetailsResponse> getAccountDetailsByNumber(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(
                managementService.getDetailsByAccountNumber(accountNumber));
    }

    @GetMapping("/accounts/number/{accountNumber}/balance")
    public ResponseEntity<Map<String, Object>> getAccountBalanceByNumber(
            @PathVariable String accountNumber) {

        AccountBalanceView view = balanceService.getBalance(accountNumber);
        return ResponseEntity.ok(Map.of(
                "accountNumber", view.accountNumber(),
                "status", view.status(),
                "balance", view.balance()
        ));
    }

    @GetMapping("/accounts/by-identity")
    public ResponseEntity<AccountDetailsResponse> getAccountByIdentity(
            @RequestParam String identityNumber,
            @RequestParam(required = false) String identityType) {

        return ResponseEntity.ok(
                managementService.getAccountByCustomerIdentity(
                        identityNumber, identityType));
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
            @RequestBody ChangeAccountStatusRequest request,
            Authentication authentication) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Request body is required.");
        }

        String actor = authentication != null ? authentication.getName() : null;

        Account updated = managementService.changeStatus(
                id,
                request.status(),
                actor
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
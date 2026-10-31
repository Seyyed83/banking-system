package com.example.banking.transaction.controller;

import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.dto.TransactionHistoryItem;
import com.example.banking.transaction.dto.TransactionResponse;
import com.example.banking.transaction.entity.FinancialTransactionType;
import com.example.banking.transaction.service.SubmissionResult;
import com.example.banking.transaction.service.TransactionHistoryService;
import com.example.banking.transaction.service.TransactionQueryService;
import com.example.banking.transaction.service.TransactionSubmissionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionSubmissionService submissionService;
    private final TransactionQueryService queryService;
    private final TransactionHistoryService historyService;

    public TransactionController(
            TransactionSubmissionService submissionService,
            TransactionQueryService queryService,
            TransactionHistoryService historyService) {
        this.submissionService = submissionService;
        this.queryService = queryService;
        this.historyService = historyService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> submit(
            @RequestBody CreateTransactionRequest request) {
        SubmissionResult result;
        try {
            result = submissionService.submit(request);
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            // Two identical requests (same clientId + idempotencyKey) raced and the second one hit
            // the unique constraint. Its transaction is rolled back; submitting once more now finds
            // the stored request and answers it as an idempotent replay instead of failing with 409.
            result = submissionService.submit(request);
        }
        String requestCode = result.request().getRequestCode();
        TransactionResponse response = queryService.getByRequestCode(requestCode);
        // Processing is asynchronous (Worker): 202 Accepted for a newly queued request,
        // 200 OK for an idempotent replay. Location points at the status resource.
        return ResponseEntity
                .status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(URI.create("/api/v1/transactions/" + requestCode))
                .body(response);
    }

    @GetMapping("/history")
    public Page<TransactionHistoryItem> history(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) FinancialTransactionType type,
            @RequestParam(required = false) String sourceAccountNumber,
            @RequestParam(required = false) String destinationAccountNumber,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return historyService.search(
                accountNumber,
                type,
                sourceAccountNumber,
                destinationAccountNumber,
                minAmount,
                maxAmount,
                from,
                to,
                page,
                size);
    }

    @GetMapping("/{requestCode}")
    public ResponseEntity<TransactionResponse> getStatus(
            @PathVariable String requestCode) {
        return ResponseEntity.ok(queryService.getByRequestCode(requestCode));
    }
}
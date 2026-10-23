package com.example.banking.transaction.controller;

import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.dto.TransactionHistoryItem;
import com.example.banking.transaction.dto.TransactionResponse;
import com.example.banking.transaction.entity.FinancialTransactionType;
import com.example.banking.transaction.service.SubmissionResult;
import com.example.banking.transaction.service.TransactionHistoryService;
import com.example.banking.transaction.service.TransactionProcessor;
import com.example.banking.transaction.service.TransactionQueryService;
import com.example.banking.transaction.service.TransactionSubmissionService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionSubmissionService submissionService;
    private final TransactionProcessor processor;
    private final TransactionQueryService queryService;
    private final TransactionHistoryService historyService;

    public TransactionController(
            TransactionSubmissionService submissionService,
            TransactionProcessor processor,
            TransactionQueryService queryService,
            TransactionHistoryService historyService) {
        this.submissionService = submissionService;
        this.processor = processor;
        this.queryService = queryService;
        this.historyService = historyService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> submit(
            @RequestBody CreateTransactionRequest request) {
        SubmissionResult result = submissionService.submit(request);
        processor.processRequest(result.request().getId());
        TransactionResponse response =
                queryService.getByRequestCode(result.request().getRequestCode());
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
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
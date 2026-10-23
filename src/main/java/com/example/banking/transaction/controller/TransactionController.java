package com.example.banking.transaction.controller;

import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.dto.TransactionResponse;
import com.example.banking.transaction.service.SubmissionResult;
import com.example.banking.transaction.service.TransactionProcessor;
import com.example.banking.transaction.service.TransactionQueryService;
import com.example.banking.transaction.service.TransactionSubmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionSubmissionService submissionService;
    private final TransactionProcessor processor;
    private final TransactionQueryService queryService;

    public TransactionController(TransactionSubmissionService submissionService,
                                 TransactionProcessor processor,
                                 TransactionQueryService queryService) {
        this.submissionService = submissionService;
        this.processor = processor;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> submit(@RequestBody CreateTransactionRequest request) {
        SubmissionResult result = submissionService.submit(request);
        processor.processRequest(result.request().getId());
        TransactionResponse response = queryService.getByRequestCode(result.request().getRequestCode());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @GetMapping("/{requestCode}")
    public ResponseEntity<TransactionResponse> getStatus(@PathVariable String requestCode) {
        return ResponseEntity.ok(queryService.getByRequestCode(requestCode));
    }
}

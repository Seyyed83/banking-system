package com.example.banking.transaction.service;

import org.springframework.stereotype.Service;

@Service
public class TransactionProcessor {

    private final TransactionExecutionService executionService;
    private final TransactionRequestService requestService;

    public TransactionProcessor(TransactionExecutionService executionService,
                                TransactionRequestService requestService) {
        this.executionService = executionService;
        this.requestService = requestService;
    }

    // Deliberately non-transactional: failed financial work rolls back first,
    // then FAILED status is recorded in its own transaction.
    public void processRequest(Long requestId) {
        try {
            executionService.execute(requestId);
        } catch (FinancialOperationException exception) {
            requestService.markFailed(requestId, exception.getFailureCode());
        }
    }
}

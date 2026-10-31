package com.example.banking.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TransactionProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionProcessor.class);

    private final TransactionExecutionService executionService;
    private final TransactionRequestService requestService;

    public TransactionProcessor(
            TransactionExecutionService executionService,
            TransactionRequestService requestService) {
        this.executionService = executionService;
        this.requestService = requestService;
    }

    public void processRequest(Long requestId) {
        String workerId = "integration-" + UUID.randomUUID();

        boolean claimed = requestService.claimById(requestId, workerId);

        if (!claimed) {
            log.debug(
                    "Transaction request {} was not claimed; "
                            + "it may already be processed or unavailable.",
                    requestId);
            return;
        }

        processRequest(requestId, workerId);
    }

    public void processRequest(Long requestId, String workerId) {
        try {
            executionService.execute(requestId, workerId);

        } catch (FinancialOperationException exception) {
            log.warn("Business validation failed for transaction request id={}, worker={}, failureCode={}, reason={}",
                    requestId, workerId, exception.getFailureCode(), exception.getMessage());
            requestService.markFailed(
                    requestId,
                    workerId,
                    exception.getFailureCode());

        } catch (Exception exception) {
            log.error(
                    "Transient error while processing transaction request {}",
                    requestId,
                    exception);

            try {
                requestService.recordTransientFailure(
                        requestId,
                        workerId,
                        "TRANSIENT_PROCESSING_ERROR");
            } catch (Exception recoveryException) {
                log.error(
                        "Could not record failure for transaction request {}. "
                                + "The lease will allow later recovery.",
                        requestId,
                        recoveryException);
            }
        }
    }
}

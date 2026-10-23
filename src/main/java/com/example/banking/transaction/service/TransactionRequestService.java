package com.example.banking.transaction.service;

import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionRequestService {

    private final TransactionRequestRepository repository;

    public TransactionRequestService(TransactionRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markFailed(Long requestId, String failureCode) {
        TransactionRequest request = repository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction request not found."));
        if (request.getStatus() == TransactionRequestStatus.COMPLETED
                || request.getStatus() == TransactionRequestStatus.FAILED) {
            return;
        }
        request.markFailed(failureCode);
    }
}

package com.example.banking.transaction.service;

import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.transaction.dto.FinancialTransactionResponse;
import com.example.banking.transaction.dto.TransactionResponse;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.repository.FinancialTransactionRepository;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionQueryService {

    private final TransactionRequestRepository requestRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    public TransactionQueryService(TransactionRequestRepository requestRepository,
                                   FinancialTransactionRepository financialTransactionRepository) {
        this.requestRepository = requestRepository;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByRequestCode(String requestCode) {
        if (requestCode == null || requestCode.isBlank() || requestCode.length() > 64) {
            throw new IllegalArgumentException("A valid request tracking code is required.");
        }
        TransactionRequest request = requestRepository.findByRequestCode(requestCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction request not found."));
        var financialTransactions = financialTransactionRepository
                .findByRequestIdOrderByCreatedAtAsc(request.getId())
                .stream()
                .map(FinancialTransactionResponse::from)
                .toList();
        return TransactionResponse.from(request, financialTransactions);
    }
}

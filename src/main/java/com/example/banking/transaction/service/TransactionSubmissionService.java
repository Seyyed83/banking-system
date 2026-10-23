package com.example.banking.transaction.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.entity.AccountRole;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.common.exception.ConflictException;
import com.example.banking.common.exception.ResourceNotFoundException;
import com.example.banking.transaction.dto.CreateTransactionRequest;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionType;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransactionSubmissionService {

    private final TransactionRequestRepository requestRepository;
    private final AccountRepository accountRepository;

    public TransactionSubmissionService(TransactionRequestRepository requestRepository,
                                        AccountRepository accountRepository) {
        this.requestRepository = requestRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public SubmissionResult submit(CreateTransactionRequest input) {
        if (input == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        String clientId = requiredText(input.clientId(), "clientId", 100);
        String key = requiredText(input.idempotencyKey(), "idempotencyKey", 100);
        TransactionType type = input.type();
        if (type == null) {
            throw new IllegalArgumentException("Transaction type is required.");
        }
        BigDecimal amount = input.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("Amount cannot have more than 4 decimal places.");
        }

        Long sourceId = null;
        Long destinationId = null;
        String sourceNumber = normalizeAccountNumber(input.sourceAccountNumber(), "sourceAccountNumber");
        String destinationNumber = normalizeAccountNumber(input.destinationAccountNumber(), "destinationAccountNumber");

        switch (type) {
            case DEPOSIT -> {
                if (sourceNumber != null || destinationNumber == null) {
                    throw new IllegalArgumentException(
                            "A deposit requires only destinationAccountNumber.");
                }
                destinationId = getCustomerAccount(destinationNumber).getId();
            }
            case WITHDRAWAL -> {
                if (sourceNumber == null || destinationNumber != null) {
                    throw new IllegalArgumentException(
                            "A withdrawal requires only sourceAccountNumber.");
                }
                sourceId = getCustomerAccount(sourceNumber).getId();
            }
            case TRANSFER -> {
                if (sourceNumber == null || destinationNumber == null) {
                    throw new IllegalArgumentException(
                            "A transfer requires sourceAccountNumber and destinationAccountNumber.");
                }
                Account source = getCustomerAccount(sourceNumber);
                Account destination = getCustomerAccount(destinationNumber);
                if (source.getId().equals(destination.getId())) {
                    throw new IllegalArgumentException("Source and destination accounts must be different.");
                }
                sourceId = source.getId();
                destinationId = destination.getId();
            }
        }

        var existing = requestRepository.findByClientIdAndIdempotencyKey(clientId, key);
        if (existing.isPresent()) {
            TransactionRequest prior = existing.get();
            boolean samePayload = prior.getTransactionType() == type
                    && equal(prior.getSourceAccountId(), sourceId)
                    && equal(prior.getDestinationAccountId(), destinationId)
                    && prior.getAmount().compareTo(amount) == 0;
            if (!samePayload) {
                throw new ConflictException(
                        "Idempotency key was already used with a different transaction payload.");
            }
            return new SubmissionResult(prior, false);
        }

        String requestCode = "REQ-" + UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                requestCode, clientId, key, type, sourceId, destinationId, amount);
        return new SubmissionResult(requestRepository.saveAndFlush(request), true);
    }

    private Account getCustomerAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        if (account.getAccountRole() != AccountRole.CUSTOMER) {
            throw new IllegalArgumentException("Financial requests can only use customer accounts.");
        }
        return account;
    }

    private static String normalizeAccountNumber(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (!result.matches("[0-9]{14}")) {
            throw new IllegalArgumentException(fieldName + " must contain exactly 14 digits.");
        }
        return result;
    }

    private static String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters.");
        }
        return result;
    }

    private static boolean equal(Long first, Long second) {
        return first == null ? second == null : first.equals(second);
    }
}

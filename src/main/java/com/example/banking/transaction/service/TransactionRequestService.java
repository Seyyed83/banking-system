package com.example.banking.transaction.service;

import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Service
public class TransactionRequestService {

    private final TransactionRequestRepository repository;

    @Value("${banking.worker.max-attempts:5}")
    private int maxAttempts;

    @Value("${banking.worker.lease-seconds:120}")
    private int leaseSeconds;

    public TransactionRequestService(
            TransactionRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<Long> claimNext(String workerId) {
        int claimed = repository.claimNext(
                workerId,
                leaseSeconds,
                maxAttempts);

        if (claimed == 0) {
            return Optional.empty();
        }

        return repository
                .findFirstByWorkerIdAndStatusOrderByCreatedAtAscIdAsc(
                        workerId,
                        TransactionRequestStatus.PROCESSING)
                .map(TransactionRequest::getId);
    }

    @Transactional
    public void markFailed(
            Long requestId,
            String workerId,
            String failureCode) {

        TransactionRequest request = findOwnedRequest(
                requestId, workerId);

        if (request == null) {
            return;
        }

        request.markFailed(normalizeFailureCode(failureCode));
    }

    @Transactional
    public void recordTransientFailure(
            Long requestId,
            String workerId,
            String failureCode) {

        TransactionRequest request = findOwnedRequest(
                requestId, workerId);

        if (request == null) {
            return;
        }

        if (request.getAttemptCount() >= maxAttempts) {
            request.markFailed("MAX_ATTEMPTS_EXCEEDED");
            return;
        }

        int exponent = Math.min(Math.max(request.getAttemptCount() - 1, 0), 8);

        long delaySeconds = Math.min(
                300L,
                1L << exponent);

        OffsetDateTime nextAttempt = OffsetDateTime.now(ZoneOffset.UTC)
                .plusSeconds(delaySeconds);

        request.scheduleRetry(
                nextAttempt,
                normalizeFailureCode(failureCode));
    }

    @Transactional
    public void failExpiredExhaustedRequests() {
        repository.failExpiredExhaustedRequests(maxAttempts);
    }

    private TransactionRequest findOwnedRequest(
            Long requestId,
            String workerId) {

        TransactionRequest request = repository
                .findByIdForUpdate(requestId)
                .orElse(null);

        if (request == null) {
            return null;
        }

        if (request.getStatus() != TransactionRequestStatus.PROCESSING) {
            return null;
        }

        if (!Objects.equals(request.getWorkerId(), workerId)) {
            return null;
        }

        return request;
    }

    private String normalizeFailureCode(String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            return "PROCESSING_ERROR";
        }

        String normalized = failureCode.trim();

        return normalized.length() <= 100
                ? normalized
                : normalized.substring(0, 100);
    }

    @Transactional
    public boolean claimById(Long requestId, String workerId) {
        return repository.claimById(
                requestId,
                workerId,
                leaseSeconds,
                maxAttempts) == 1;
    }

}

package com.example.banking.transaction.service;

import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.repository.TransactionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionRequestServiceTest {

    @Mock
    private TransactionRequestRepository repository;

    @InjectMocks
    private TransactionRequestService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "leaseSeconds", 120);
    }

    @Test
    void claimNextReturnsEmptyWhenNoRequestCanBeClaimed() {
        when(repository.claimNext("worker-1", 120, 5))
                .thenReturn(0);

        Optional<Long> result = service.claimNext("worker-1");

        assertTrue(result.isEmpty());
        verify(repository, never())
                .findFirstByWorkerIdAndStatusOrderByCreatedAtAscIdAsc(
                        anyString(), any());
    }

    @Test
    void claimNextReturnsClaimedRequestId() {
        when(repository.claimNext("worker-1", 120, 5))
                .thenReturn(1);

        when(repository
                .findFirstByWorkerIdAndStatusOrderByCreatedAtAscIdAsc(
                        "worker-1", TransactionRequestStatus.PROCESSING))
                .thenReturn(Optional.of(request(42L, 1, "worker-1")));

        Optional<Long> result = service.claimNext("worker-1");

        assertEquals(Optional.of(42L), result);
    }

    @Test
    void claimByIdReturnsTrueWhenRepositoryClaimsRequest() {
        when(repository.claimById(42L, "worker-1", 120, 5))
                .thenReturn(1);

        assertTrue(service.claimById(42L, "worker-1"));
    }

    @Test
    void claimByIdReturnsFalseWhenRequestIsNotEligible() {
        when(repository.claimById(42L, "worker-1", 120, 5))
                .thenReturn(0);

        assertFalse(service.claimById(42L, "worker-1"));
    }

    @Test
    void transientFailureSchedulesRetryAndReleasesWorker() {
        TransactionRequest request = request(42L, 1, "worker-1");

        when(repository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(request));

        service.recordTransientFailure(
                42L, "worker-1", "TEMPORARY_ERROR");

        assertEquals(TransactionRequestStatus.PENDING, request.getStatus());
        assertNull(request.getWorkerId());
        assertNull(request.getLeaseUntil());
        assertNull(request.getProcessedAt());
        assertEquals("TEMPORARY_ERROR", request.getFailureCode());
        assertNotNull(request.getNextAttemptAt());
        assertTrue(request.getNextAttemptAt().isAfter(
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void transientFailureAtMaxAttemptsMarksRequestFailed() {
        TransactionRequest request = request(42L, 5, "worker-1");

        when(repository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(request));

        service.recordTransientFailure(
                42L, "worker-1", "TEMPORARY_ERROR");

        assertEquals(TransactionRequestStatus.FAILED, request.getStatus());
        assertEquals("MAX_ATTEMPTS_EXCEEDED", request.getFailureCode());
        assertNull(request.getWorkerId());
        assertNull(request.getLeaseUntil());
        assertNotNull(request.getProcessedAt());
    }

    @Test
    void transientFailureDoesNothingWhenWorkerDoesNotOwnRequest() {
        TransactionRequest request = request(42L, 1, "worker-2");

        when(repository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(request));

        service.recordTransientFailure(
                42L, "worker-1", "TEMPORARY_ERROR");

        assertEquals(TransactionRequestStatus.PROCESSING, request.getStatus());
        assertEquals("worker-2", request.getWorkerId());
        assertNull(request.getFailureCode());
    }

    @Test
    void transientFailureDoesNothingWhenRequestIsMissing() {
        when(repository.findByIdForUpdate(42L))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.recordTransientFailure(
                        42L, "worker-1", "TEMPORARY_ERROR"));
    }

    @Test
    void expiredExhaustedRequestsAreDelegatedToRepository() {
        service.failExpiredExhaustedRequests();

        verify(repository).failExpiredExhaustedRequests(5);
    }

    private TransactionRequest request(
            Long id,
            int attempts,
            String workerId) {

        TransactionRequest request = new TransactionRequest(
                "REQ-" + id,
                "client-1",
                "key-" + id,
                com.example.banking.transaction.entity.TransactionType.DEPOSIT,
                null,
                100L,
                new BigDecimal("100.00"));

        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(
                request, "status", TransactionRequestStatus.PROCESSING);
        ReflectionTestUtils.setField(request, "attemptCount", attempts);
        ReflectionTestUtils.setField(request, "workerId", workerId);
        ReflectionTestUtils.setField(
                request, "leaseUntil",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(2));

        return request;
    }
}

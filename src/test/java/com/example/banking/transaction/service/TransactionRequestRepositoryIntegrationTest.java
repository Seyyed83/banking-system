package com.example.banking.transaction.service;

import com.example.banking.account.entity.Account;
import com.example.banking.account.repository.AccountRepository;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.entity.TransactionType;
import com.example.banking.transaction.repository.TransactionRequestRepository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "banking.worker.poll-interval-ms=3600000"
})
@Transactional
class TransactionRequestRepositoryIntegrationTest {

    @Autowired
    private TransactionRequestRepository repository;

    @Autowired
    private TransactionRequestService requestService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void claimByIdPersistsProcessingStatusWorkerLeaseAndAttemptCount() {
        TransactionRequest request = createRequest();
        String workerId = "integration-worker-1";

        assertTrue(requestService.claimById(request.getId(), workerId));

        TransactionRequest stored = repository.findById(request.getId())
                .orElseThrow();

        assertEquals(TransactionRequestStatus.PROCESSING, stored.getStatus());
        assertEquals(workerId, stored.getWorkerId());
        assertEquals(1, stored.getAttemptCount());
        assertNotNull(stored.getLeaseUntil());
        assertTrue(stored.getLeaseUntil().isAfter(
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(5)));
    }

    @Test
    void requestCannotBeClaimedTwiceByDifferentWorkers() {
        TransactionRequest request = createRequest();

        assertTrue(requestService.claimById(
                request.getId(), "worker-first"));

        assertFalse(requestService.claimById(
                request.getId(), "worker-second"));

        TransactionRequest stored = repository.findById(request.getId())
                .orElseThrow();

        assertEquals(TransactionRequestStatus.PROCESSING, stored.getStatus());
        assertEquals("worker-first", stored.getWorkerId());
        assertEquals(1, stored.getAttemptCount());
    }

    @Test
    void requestIsNotClaimedBeforeItsNextAttemptTime() {
        TransactionRequest request = createRequest();

        ReflectionTestUtils.setField(
                request,
                "nextAttemptAt",
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        repository.saveAndFlush(request);

        assertFalse(requestService.claimById(
                request.getId(), "retry-worker"));
    }

    @Test
    void requestAtMaximumAttemptsCannotBeClaimedAgain() {
        TransactionRequest request = createRequest();

        ReflectionTestUtils.setField(request, "attemptCount", 5);
        repository.saveAndFlush(request);

        assertFalse(requestService.claimById(
                request.getId(), "exhausted-worker"));
    }

    private TransactionRequest createRequest() {
        String suffix = UUID.randomUUID().toString();
        Long destinationAccountId = createCustomerAccount();

        TransactionRequest request = new TransactionRequest(
                "REQ-IT-" + suffix,
                "integration-client-" + suffix,
                "integration-key-" + suffix,
                TransactionType.DEPOSIT,
                null,
                destinationAccountId,
                new BigDecimal("12.00"));

        return repository.saveAndFlush(request);
    }

    private Long createCustomerAccount() {
        String identity = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 24);

        jdbcTemplate.update(
                """
                INSERT INTO CUSTOMERS
                    (CUSTOMER_TYPE, IDENTITY_TYPE, IDENTITY_NUMBER, FULL_NAME)
                VALUES (?, ?, ?, ?)
                """,
                "INDIVIDUAL",
                "NATIONAL_ID",
                identity,
                "Transaction Request Integration Test");

        Long customerId = jdbcTemplate.queryForObject(
                """
                SELECT ID FROM CUSTOMERS
                WHERE IDENTITY_TYPE = ? AND IDENTITY_NUMBER = ?
                """,
                Long.class,
                "NATIONAL_ID",
                identity);

        String accountNumber;

        do {
            accountNumber = Long.toString(
                    ThreadLocalRandom.current().nextLong(
                            10_000_000_000_000L,
                            100_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = Account.openCustomerAccount(
                accountNumber, customerId);

        return accountRepository.saveAndFlush(account).getId();
    }

    @Test
    void transientFailuresAreRetriedUntilMaximumAttemptsThenFailed() {
        TransactionRequest request = createRequest();
        Long requestId = request.getId();
        String workerId = "retry-integration-worker";

        for (int expectedAttempt = 1; expectedAttempt <= 5; expectedAttempt++) {

            if (expectedAttempt > 1) {
                entityManager.flush();
                entityManager.clear();

                TransactionRequest pending =
                        repository.findById(requestId).orElseThrow();

                assertEquals(
                        TransactionRequestStatus.PENDING,
                        pending.getStatus());

                // Make the scheduled retry eligible without sleeping.
                ReflectionTestUtils.setField(
                        pending,
                        "nextAttemptAt",
                        java.time.OffsetDateTime.now(
                                java.time.ZoneOffset.UTC).minusSeconds(1));

                repository.saveAndFlush(pending);
                entityManager.clear();
            }

            assertTrue(
                    requestService.claimById(requestId, workerId),
                    "Attempt " + expectedAttempt + " should be claimable.");

            entityManager.flush();
            entityManager.clear();

            TransactionRequest processing =
                    repository.findById(requestId).orElseThrow();

            assertEquals(
                    TransactionRequestStatus.PROCESSING,
                    processing.getStatus());

            assertEquals(expectedAttempt, processing.getAttemptCount());
            assertEquals(workerId, processing.getWorkerId());

            requestService.recordTransientFailure(
                    requestId,
                    workerId,
                    "TRANSIENT_TEST_FAILURE");

            entityManager.flush();
            entityManager.clear();

            TransactionRequest afterFailure =
                    repository.findById(requestId).orElseThrow();

            if (expectedAttempt < 5) {
                assertEquals(
                        TransactionRequestStatus.PENDING,
                        afterFailure.getStatus());

                assertEquals(
                        "TRANSIENT_TEST_FAILURE",
                        afterFailure.getFailureCode());

                assertNull(afterFailure.getWorkerId());
                assertNull(afterFailure.getLeaseUntil());
                assertNotNull(afterFailure.getNextAttemptAt());

                assertTrue(
                        afterFailure.getNextAttemptAt().isAfter(
                                java.time.OffsetDateTime.now(
                                        java.time.ZoneOffset.UTC)),
                        "Retry must be scheduled in the future.");

                assertFalse(
                        requestService.claimById(
                                requestId,
                                "too-early-worker"),
                        "A request must not be claimed before its retry time.");
            } else {
                assertEquals(
                        TransactionRequestStatus.FAILED,
                        afterFailure.getStatus());

                assertEquals(5, afterFailure.getAttemptCount());

                assertEquals(
                        "MAX_ATTEMPTS_EXCEEDED",
                        afterFailure.getFailureCode());

                assertNull(afterFailure.getWorkerId());
                assertNull(afterFailure.getLeaseUntil());

                assertFalse(
                        requestService.claimById(
                                requestId,
                                "worker-after-exhaustion"),
                        "An exhausted request must not be claimed again.");
            }
        }
    }

    @Test
    void expiredProcessingLeaseCanBeRecoveredByAnotherWorker() {
        TransactionRequest request = createRequest();
        Long requestId = request.getId();

        // Worker 1 claims the request.
        assertTrue(requestService.claimById(
                requestId, "worker-original"));

        entityManager.flush();
        entityManager.clear();

        TransactionRequest abandoned =
                repository.findById(requestId).orElseThrow();

        assertEquals(
                TransactionRequestStatus.PROCESSING,
                abandoned.getStatus());
        assertEquals("worker-original", abandoned.getWorkerId());
        assertEquals(1, abandoned.getAttemptCount());

        // Simulate a crashed worker by expiring its lease.
        ReflectionTestUtils.setField(
                abandoned,
                "leaseUntil",
                java.time.OffsetDateTime.now(
                        java.time.ZoneOffset.UTC).minusSeconds(10));

        repository.saveAndFlush(abandoned);
        entityManager.clear();

        // Worker 2 attempts to recover the expired request.
        java.util.Optional<Long> recovered =
                requestService.claimNext("worker-recovery");

        assertEquals(
                java.util.Optional.of(requestId),
                recovered,
                "The worker should recover the request after lease expiry.");

        entityManager.flush();
        entityManager.clear();

        TransactionRequest afterRecovery =
                repository.findById(requestId).orElseThrow();

        assertEquals(
                TransactionRequestStatus.PROCESSING,
                afterRecovery.getStatus());
        assertEquals("worker-recovery", afterRecovery.getWorkerId());
        assertEquals(2, afterRecovery.getAttemptCount());

        assertNotNull(afterRecovery.getLeaseUntil());
        assertTrue(afterRecovery.getLeaseUntil().isAfter(
                java.time.OffsetDateTime.now(
                        java.time.ZoneOffset.UTC)));

        // The same request was recovered; no duplicate request was created.
        assertTrue(repository.findById(requestId).isPresent());
    }

    @Test
    void cleanupMarksExpiredExhaustedRequestAsFailed() {
        TransactionRequest request = createRequest();

        ReflectionTestUtils.setField(
                request, "status", TransactionRequestStatus.PROCESSING);
        ReflectionTestUtils.setField(request, "attemptCount", 5);
        ReflectionTestUtils.setField(
                request, "workerId", "abandoned-exhausted-worker");
        ReflectionTestUtils.setField(
                request,
                "leaseUntil",
                java.time.OffsetDateTime.now(
                        java.time.ZoneOffset.UTC).minusSeconds(30));

        repository.saveAndFlush(request);
        Long requestId = request.getId();

        requestService.failExpiredExhaustedRequests();

        entityManager.flush();
        entityManager.clear();

        TransactionRequest stored =
                repository.findById(requestId).orElseThrow();

        assertEquals(
                TransactionRequestStatus.FAILED,
                stored.getStatus(),
                "An expired request at the attempt limit should fail.");
    }

    @Test
    void cleanupDoesNotFailRequestBelowMaximumAttempts() {
        TransactionRequest request = createRequest();

        ReflectionTestUtils.setField(
                request, "status", TransactionRequestStatus.PROCESSING);
        ReflectionTestUtils.setField(request, "attemptCount", 4);
        ReflectionTestUtils.setField(
                request, "workerId", "abandoned-retryable-worker");
        ReflectionTestUtils.setField(
                request,
                "leaseUntil",
                java.time.OffsetDateTime.now(
                        java.time.ZoneOffset.UTC).minusSeconds(30));

        repository.saveAndFlush(request);
        Long requestId = request.getId();

        requestService.failExpiredExhaustedRequests();

        entityManager.flush();
        entityManager.clear();

        TransactionRequest stored =
                repository.findById(requestId).orElseThrow();

        assertEquals(
                TransactionRequestStatus.PROCESSING,
                stored.getStatus(),
                "An expired request below the attempt limit must not be failed by exhausted-request cleanup.");
    }
}
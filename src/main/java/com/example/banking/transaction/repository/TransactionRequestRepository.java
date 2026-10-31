
package com.example.banking.transaction.repository;

import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRequestRepository
        extends JpaRepository<TransactionRequest, Long> {

    Optional<TransactionRequest> findByClientIdAndIdempotencyKey(
            String clientId,
            String idempotencyKey);

    Optional<TransactionRequest> findByRequestCode(String requestCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TransactionRequest r where r.id = :id")
    Optional<TransactionRequest> findByIdForUpdate(
            @Param("id") Long id);

    Optional<TransactionRequest>
    findFirstByWorkerIdAndStatusOrderByCreatedAtAscIdAsc(
            String workerId,
            TransactionRequestStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE TRANSACTION_REQUESTS
               SET STATUS = 'PROCESSING',
                   ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                   WORKER_ID = :workerId,
                   LEASE_UNTIL = SYSTIMESTAMP
                       + NUMTODSINTERVAL(:leaseSeconds, 'SECOND'),
                   FAILURE_CODE = NULL
             WHERE ID = :requestId
               AND STATUS = 'PENDING'
               AND NEXT_ATTEMPT_AT <= SYSTIMESTAMP
               AND ATTEMPT_COUNT < :maxAttempts
            """, nativeQuery = true)
    int claimById(
            @Param("requestId") Long requestId,
            @Param("workerId") String workerId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("maxAttempts") int maxAttempts);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE TRANSACTION_REQUESTS r
               SET STATUS = 'PROCESSING',
                   ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                   WORKER_ID = :workerId,
                   LEASE_UNTIL = SYSTIMESTAMP
                       + NUMTODSINTERVAL(:leaseSeconds, 'SECOND'),
                   FAILURE_CODE = NULL
             WHERE r.ID = (
                   SELECT candidate.ID
                     FROM (
                           SELECT ID
                             FROM TRANSACTION_REQUESTS
                            WHERE (
                                  STATUS = 'PENDING'
                                  AND NEXT_ATTEMPT_AT <= SYSTIMESTAMP
                                  AND ATTEMPT_COUNT < :maxAttempts
                            )
                               OR (
                                  STATUS = 'PROCESSING'
                                  AND LEASE_UNTIL <= SYSTIMESTAMP
                                  AND ATTEMPT_COUNT < :maxAttempts
                            )
                            ORDER BY CREATED_AT ASC, ID ASC
                          ) candidate
                    WHERE ROWNUM = 1
             )
               AND (
                    (
                        STATUS = 'PENDING'
                        AND NEXT_ATTEMPT_AT <= SYSTIMESTAMP
                        AND ATTEMPT_COUNT < :maxAttempts
                    )
                    OR (
                        STATUS = 'PROCESSING'
                        AND LEASE_UNTIL <= SYSTIMESTAMP
                        AND ATTEMPT_COUNT < :maxAttempts
                    )
               )
            """, nativeQuery = true)
    int claimNext(
            @Param("workerId") String workerId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("maxAttempts") int maxAttempts);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from TransactionRequest r
             where r.status = :status
               and r.leaseUntil <= :now
               and r.attemptCount >= :maxAttempts
            """)
    List<TransactionRequest> findExpiredExhaustedForUpdate(
            @Param("status") TransactionRequestStatus status,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") OffsetDateTime now);
}

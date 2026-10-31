package com.example.banking.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(String eventId);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByIdAsc(
            String aggregateType, String aggregateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);

    Optional<OutboxEvent> findFirstByPublisherIdAndStatusOrderByIdAsc(
            String publisherId, OutboxEventStatus status);

    /**
     * Atomically claims the oldest due event (PENDING and due, or PROCESSING with an expired
     * lease) for one publisher. Same single-statement claim pattern as the transaction worker.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE OUTBOX_EVENTS e
               SET STATUS = 'PROCESSING',
                   ATTEMPT_COUNT = ATTEMPT_COUNT + 1,
                   PUBLISHER_ID = :publisherId,
                   LEASE_UNTIL = SYSTIMESTAMP
                       + NUMTODSINTERVAL(:leaseSeconds, 'SECOND')
             WHERE e.ID = (
                   SELECT candidate.ID
                     FROM (
                           SELECT ID
                             FROM OUTBOX_EVENTS
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
                            ORDER BY ID ASC
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
            @Param("publisherId") String publisherId,
            @Param("leaseSeconds") int leaseSeconds,
            @Param("maxAttempts") int maxAttempts);

    /** A publisher that died after its last allowed attempt leaves a PROCESSING row behind. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE OUTBOX_EVENTS
               SET STATUS = 'FAILED',
                   PUBLISHER_ID = NULL,
                   LEASE_UNTIL = NULL,
                   LAST_ERROR = 'Lease expired after the maximum number of attempts'
             WHERE STATUS = 'PROCESSING'
               AND LEASE_UNTIL <= SYSTIMESTAMP
               AND ATTEMPT_COUNT >= :maxAttempts
            """, nativeQuery = true)
    int failExpiredExhausted(@Param("maxAttempts") int maxAttempts);
}

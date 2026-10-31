package com.example.banking.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * Database side of publishing: claim, acknowledge and fail outbox rows. Each method is its own
 * short Oracle transaction; the Kafka send itself happens between them, outside any transaction.
 */
@Service
public class OutboxPublisherService {

    private final OutboxEventRepository repository;

    @Value("${banking.kafka.publisher.max-attempts:10}")
    private int maxAttempts = 10;

    @Value("${banking.kafka.publisher.lease-seconds:60}")
    private int leaseSeconds = 60;

    public OutboxPublisherService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<ClaimedOutboxEvent> claimNext(String publisherId) {
        if (repository.claimNext(publisherId, leaseSeconds, maxAttempts) == 0) {
            return Optional.empty();
        }
        return repository
                .findFirstByPublisherIdAndStatusOrderByIdAsc(publisherId, OutboxEventStatus.PROCESSING)
                .map(ClaimedOutboxEvent::from);
    }

    /** @return false if this publisher no longer owns the row (lease expired and re-claimed). */
    @Transactional
    public boolean markPublished(Long id, String publisherId) {
        OutboxEvent event = findOwned(id, publisherId);
        if (event == null) {
            return false;
        }
        event.markPublished();
        return true;
    }

    @Transactional
    public void recordFailure(Long id, String publisherId, String error) {
        OutboxEvent event = findOwned(id, publisherId);
        if (event == null) {
            return;
        }
        if (event.getAttemptCount() >= maxAttempts) {
            event.markFailed(error);
            return;
        }
        int exponent = Math.min(Math.max(event.getAttemptCount() - 1, 0), 8);
        long delaySeconds = Math.min(300L, 1L << exponent);
        event.scheduleRetry(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds), error);
    }

    @Transactional
    public void failExpiredExhausted() {
        repository.failExpiredExhausted(maxAttempts);
    }

    private OutboxEvent findOwned(Long id, String publisherId) {
        OutboxEvent event = repository.findByIdForUpdate(id).orElse(null);
        if (event == null
                || event.getStatus() != OutboxEventStatus.PROCESSING
                || !Objects.equals(event.getPublisherId(), publisherId)) {
            return null;
        }
        return event;
    }
}

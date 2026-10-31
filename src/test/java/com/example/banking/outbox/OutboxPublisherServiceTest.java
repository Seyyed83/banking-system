package com.example.banking.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboxPublisherServiceTest {

    private OutboxEventRepository repository;
    private OutboxPublisherService service;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        service = new OutboxPublisherService(repository);
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "leaseSeconds", 60);
    }

    private OutboxEvent event(OutboxEventStatus status, int attempts, String publisherId) {
        OutboxEvent event = new OutboxEvent("TRANSACTION_REQUEST", "REQ-1", "TRANSACTION_COMPLETED", "{}");
        ReflectionTestUtils.setField(event, "id", 7L);
        ReflectionTestUtils.setField(event, "status", status);
        ReflectionTestUtils.setField(event, "attemptCount", attempts);
        ReflectionTestUtils.setField(event, "publisherId", publisherId);
        ReflectionTestUtils.setField(event, "nextAttemptAt", OffsetDateTime.now(ZoneOffset.UTC));
        return event;
    }

    @Test
    void claimNextReturnsEmptyWhenNothingIsDue() {
        when(repository.claimNext("p-1", 60, 3)).thenReturn(0);

        assertTrue(service.claimNext("p-1").isEmpty());
        verify(repository, never()).findFirstByPublisherIdAndStatusOrderByIdAsc(any(), any());
    }

    @Test
    void claimNextReturnsSnapshotOfClaimedRow() {
        when(repository.claimNext("p-1", 60, 3)).thenReturn(1);
        OutboxEvent claimed = event(OutboxEventStatus.PROCESSING, 1, "p-1");
        when(repository.findFirstByPublisherIdAndStatusOrderByIdAsc("p-1", OutboxEventStatus.PROCESSING))
                .thenReturn(Optional.of(claimed));

        ClaimedOutboxEvent result = service.claimNext("p-1").orElseThrow();

        assertEquals(7L, result.id());
        assertEquals("REQ-1", result.aggregateId());
        assertEquals("TRANSACTION_COMPLETED", result.eventType());
    }

    @Test
    void markPublishedByOwnerCompletesEvent() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 1, "p-1");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));

        assertTrue(service.markPublished(7L, "p-1"));

        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getPublisherId());
    }

    @Test
    void markPublishedByStalePublisherIsRejected() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 2, "p-2");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));

        assertFalse(service.markPublished(7L, "p-1"));

        assertEquals(OutboxEventStatus.PROCESSING, event.getStatus());
    }

    @Test
    void failureSchedulesRetryWithBackoff() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 1, "p-1");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));

        service.recordFailure(7L, "p-1", "broker down");

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals("broker down", event.getLastError());
        assertNull(event.getPublisherId());
        assertTrue(event.getNextAttemptAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void failureAtMaxAttemptsMarksEventFailed() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 3, "p-1");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));

        service.recordFailure(7L, "p-1", "broker down");

        assertEquals(OutboxEventStatus.FAILED, event.getStatus());
    }

    @Test
    void failureByNonOwnerChangesNothing() {
        OutboxEvent event = event(OutboxEventStatus.PROCESSING, 1, "p-2");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(event));

        service.recordFailure(7L, "p-1", "broker down");

        assertEquals(OutboxEventStatus.PROCESSING, event.getStatus());
        assertEquals("p-2", event.getPublisherId());
    }

    @Test
    void expiredExhaustedRowsAreDelegatedToRepository() {
        service.failExpiredExhausted();

        verify(repository).failExpiredExhausted(3);
    }
}

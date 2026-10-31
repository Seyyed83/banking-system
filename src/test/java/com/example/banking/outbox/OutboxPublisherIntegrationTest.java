package com.example.banking.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real Oracle test of the outbox claim / acknowledge cycle. Opt in with -Doracle.integration=true. */
@SpringBootTest(properties = {
        "banking.worker.poll-interval-ms=3600000",
        "banking.kafka.enabled=false",
        "banking.kafka.consumer.enabled=false"})
@EnabledIfSystemProperty(named = "oracle.integration", matches = "true")
@TestPropertySource(properties = {"banking.security.username=test-admin", "banking.security.password=test-password"})
class OutboxPublisherIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OutboxEventRepository repository;
    @Autowired private OutboxPublisherService service;

    private OutboxEvent newEvent() {
        // Park every other pending row so this test only ever claims its own event.
        jdbcTemplate.update("UPDATE OUTBOX_EVENTS SET STATUS = 'FAILED' WHERE STATUS IN ('PENDING', 'PROCESSING')");
        return repository.saveAndFlush(new OutboxEvent(
                "TRANSACTION_REQUEST", "REQ-" + UUID.randomUUID(), "TRANSACTION_COMPLETED", "{\"k\":\"v\"}"));
    }

    @Test
    void claimedEventCanBePublishedOnlyByItsOwner() {
        OutboxEvent saved = newEvent();

        ClaimedOutboxEvent claimed = service.claimNext("it-publisher-1").orElseThrow();
        assertEquals(saved.getId(), claimed.id());
        assertTrue(service.claimNext("it-publisher-2").isEmpty(), "a leased event must not be claimed twice");

        assertFalse(service.markPublished(saved.getId(), "it-publisher-2"));
        assertTrue(service.markPublished(saved.getId(), "it-publisher-1"));

        OutboxEvent stored = repository.findById(saved.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PUBLISHED, stored.getStatus());
        assertNotNull(stored.getPublishedAt());
        assertEquals("{\"k\":\"v\"}", stored.getPayload());
    }

    @Test
    void failedPublishGoesBackToPendingWithBackoff() {
        OutboxEvent saved = newEvent();
        service.claimNext("it-publisher-1").orElseThrow();

        service.recordFailure(saved.getId(), "it-publisher-1", "broker down");

        OutboxEvent stored = repository.findById(saved.getId()).orElseThrow();
        assertEquals(OutboxEventStatus.PENDING, stored.getStatus());
        assertEquals("broker down", stored.getLastError());
        assertEquals(1, stored.getAttemptCount());
        assertTrue(service.claimNext("it-publisher-1").isEmpty(), "retry must wait for NEXT_ATTEMPT_AT");
    }
}

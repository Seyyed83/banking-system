package com.example.banking.outbox;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** One integration event waiting to be published (transactional outbox row). */
@Entity
@Table(name = "OUTBOX_EVENTS")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "EVENT_ID", nullable = false, length = 36, updatable = false)
    private String eventId;

    @Column(name = "AGGREGATE_TYPE", nullable = false, length = 50, updatable = false)
    private String aggregateType;

    @Column(name = "AGGREGATE_ID", nullable = false, length = 100, updatable = false)
    private String aggregateId;

    @Column(name = "EVENT_TYPE", nullable = false, length = 100, updatable = false)
    private String eventType;

    @Lob
    @Column(name = "PAYLOAD", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private int attemptCount;

    @Column(name = "PUBLISHER_ID", length = 100)
    private String publisherId;

    @Column(name = "LEASE_UNTIL")
    private OffsetDateTime leaseUntil;

    @Column(name = "NEXT_ATTEMPT_AT", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "PUBLISHED_AT")
    private OffsetDateTime publishedAt;

    @Column(name = "LAST_ERROR", length = 1000)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
    }

    /** The payload embeds the event ID, so the service fixes the ID before the row is saved. */
    void useEventId(String eventId) {
        this.eventId = eventId;
    }

    @PrePersist
    protected void beforeInsert() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        publisherId = null;
        leaseUntil = null;
        lastError = null;
    }

    public void scheduleRetry(OffsetDateTime nextAttempt, String error) {
        status = OutboxEventStatus.PENDING;
        nextAttemptAt = nextAttempt;
        publisherId = null;
        leaseUntil = null;
        lastError = truncate(error);
    }

    public void markFailed(String error) {
        status = OutboxEventStatus.FAILED;
        publisherId = null;
        leaseUntil = null;
        lastError = truncate(error);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxEventStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getPublisherId() { return publisherId; }
    public OffsetDateTime getLeaseUntil() { return leaseUntil; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}

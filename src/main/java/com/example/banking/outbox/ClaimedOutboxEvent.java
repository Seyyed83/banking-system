package com.example.banking.outbox;

/** Immutable snapshot of a claimed outbox row, safe to use outside the claiming transaction. */
public record ClaimedOutboxEvent(
        Long id,
        String eventId,
        String aggregateId,
        String eventType,
        String payload) {

    static ClaimedOutboxEvent from(OutboxEvent event) {
        return new ClaimedOutboxEvent(
                event.getId(),
                event.getEventId(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload());
    }
}

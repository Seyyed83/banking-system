package com.example.banking.outbox;

import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.TransactionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Writes outbox rows. Every method REQUIRES an already-running database transaction
 * (MANDATORY), so an event can never be written outside the transaction that produced it.
 */
@Service
public class OutboxEventService {

    public static final String AGGREGATE_TRANSACTION_REQUEST = "TRANSACTION_REQUEST";
    public static final String TRANSACTION_COMPLETED = "TRANSACTION_COMPLETED";
    public static final String TRANSACTION_FAILED = "TRANSACTION_FAILED";

    private final OutboxEventRepository repository;

    public OutboxEventService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordCompleted(
            TransactionRequest request,
            List<FinancialTransaction> transactions) {

        String eventId = UUID.randomUUID().toString();
        String payload = OutboxPayloads.completed(eventId, now(), request, transactions);
        return save(eventId, request, TRANSACTION_COMPLETED, payload);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordFailed(TransactionRequest request) {
        String eventId = UUID.randomUUID().toString();
        String payload = OutboxPayloads.failed(eventId, now(), request);
        return save(eventId, request, TRANSACTION_FAILED, payload);
    }

    private OutboxEvent save(
            String eventId,
            TransactionRequest request,
            String eventType,
            String payload) {

        OutboxEvent event = new OutboxEvent(
                AGGREGATE_TRANSACTION_REQUEST,
                request.getRequestCode(),
                eventType,
                payload);
        event.useEventId(eventId);
        return repository.save(event);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}

package com.example.banking.audit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Audit / notification consumer for finalized transaction events.
 *
 * <p>Kafka delivers at least once, so every event is first written to TRANSACTION_EVENT_LOG
 * under a UNIQUE(EVENT_ID) constraint; a duplicate delivery is recognised and ignored, and the
 * notification step runs only for the first delivery.
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final JdbcTemplate jdbcTemplate;

    public TransactionEventConsumer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(
            topics = "${banking.kafka.topic:banking.transaction-events}",
            groupId = "${banking.kafka.consumer.group-id:banking-audit}",
            autoStartup = "${banking.kafka.consumer.enabled:true}")
    public void onMessage(ConsumerRecord<String, String> record) {
        handle(headerValue(record, "eventId"),
                headerValue(record, "eventType"),
                record.key(),
                record.value());
    }

    /** @return true if the event was new and has been recorded. */
    boolean handle(String eventId, String eventType, String aggregateId, String payload) {
        if (eventId == null || eventId.isBlank() || eventType == null || aggregateId == null || payload == null) {
            log.warn("Ignoring malformed transaction event (eventId={}, type={}, key={}).",
                    eventId, eventType, aggregateId);
            return false;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO TRANSACTION_EVENT_LOG (EVENT_ID, EVENT_TYPE, AGGREGATE_ID, PAYLOAD) "
                            + "VALUES (?, ?, ?, ?)",
                    eventId, eventType, aggregateId, payload);
        } catch (DuplicateKeyException duplicate) {
            log.debug("Duplicate delivery of event {} ignored.", eventId);
            return false;
        }
        // Notification hook: a real system would call an SMS / e-mail / push gateway here.
        log.info("Notification: request {} -> {} (eventId={})", aggregateId, eventType, eventId);
        return true;
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null || header.value() == null
                ? null
                : new String(header.value(), StandardCharsets.UTF_8);
    }
}

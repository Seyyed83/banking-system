package com.example.banking.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Drains the transactional outbox into Kafka (at-least-once).
 *
 * <p>Oracle stays authoritative: an event is marked PUBLISHED only after the broker acknowledged
 * it. If the process dies between the send and the acknowledgement the event is sent again after
 * its lease expires, so consumers must de-duplicate by {@code eventId} (the audit consumer does).
 * Kafka outages never affect money movement - events simply stay PENDING until the broker is back.
 */
@Component
public class OutboxKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxKafkaPublisher.class);

    static final String HEADER_EVENT_ID = "eventId";
    static final String HEADER_EVENT_TYPE = "eventType";

    private final OutboxPublisherService publisherService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String instanceId = "publisher-" + UUID.randomUUID();

    @Value("${banking.kafka.enabled:true}")
    private boolean enabled = true;

    @Value("${banking.kafka.topic:banking.transaction-events}")
    private String topic = "banking.transaction-events";

    @Value("${banking.kafka.publisher.batch-size:50}")
    private int batchSize = 50;

    @Value("${banking.kafka.publisher.send-timeout-seconds:10}")
    private long sendTimeoutSeconds = 10;

    public OutboxKafkaPublisher(
            OutboxPublisherService publisherService,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.publisherService = publisherService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${banking.kafka.publisher.poll-interval-ms:500}")
    public void poll() {
        if (!enabled) {
            return;
        }
        try {
            publisherService.failExpiredExhausted();

            int limit = batchSize > 0 ? batchSize : 50;
            for (int i = 0; i < limit; i++) {
                String publisherId = instanceId + "-" + UUID.randomUUID();
                Optional<ClaimedOutboxEvent> claimed = publisherService.claimNext(publisherId);
                if (claimed.isEmpty()) {
                    break;
                }
                if (!publish(claimed.get(), publisherId)) {
                    // Broker trouble: stop this cycle instead of burning attempts on every row.
                    break;
                }
            }
        } catch (Exception exception) {
            log.error("Outbox publisher poll failed for {}.", instanceId, exception);
        }
    }

    /** @return true if the event reached Kafka and was acknowledged in Oracle. */
    boolean publish(ClaimedOutboxEvent event, String publisherId) {
        try {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, event.aggregateId(), event.payload());
            record.headers().add(HEADER_EVENT_ID, event.eventId().getBytes(StandardCharsets.UTF_8));
            record.headers().add(HEADER_EVENT_TYPE, event.eventType().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).get(sendTimeoutSeconds, TimeUnit.SECONDS);

            if (!publisherService.markPublished(event.id(), publisherId)) {
                log.warn("Outbox event {} was published but its claim was lost; it may be sent again.",
                        event.eventId());
            }
            log.info("Published outbox event eventId={}, type={}, key={}",
                    event.eventId(), event.eventType(), event.aggregateId());
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            recordFailure(event, publisherId, interrupted);
            return false;
        } catch (Exception exception) {
            recordFailure(event, publisherId, exception);
            return false;
        }
    }

    private void recordFailure(ClaimedOutboxEvent event, String publisherId, Exception exception) {
        log.warn("Publishing outbox event eventId={} failed: {}", event.eventId(), exception.toString());
        try {
            publisherService.recordFailure(event.id(), publisherId, exception.toString());
        } catch (RuntimeException secondary) {
            log.error("Could not record publish failure for outbox event {}.", event.eventId(), secondary);
        }
    }
}

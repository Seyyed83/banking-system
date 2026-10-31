package com.example.banking.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class OutboxKafkaPublisherTest {

    private OutboxPublisherService service;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxKafkaPublisher publisher;

    private final ClaimedOutboxEvent event = new ClaimedOutboxEvent(
            7L, "evt-1", "REQ-1", "TRANSACTION_COMPLETED", "{\"a\":1}");

    @BeforeEach
    void setUp() {
        service = mock(OutboxPublisherService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        publisher = new OutboxKafkaPublisher(service, kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "topic", "events");
        ReflectionTestUtils.setField(publisher, "sendTimeoutSeconds", 1L);
    }

    @Test
    void successfulSendMarksEventPublishedWithKeyAndHeaders() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));
        when(service.markPublished(7L, "p-1")).thenReturn(true);

        assertTrue(publisher.publish(event, "p-1"));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertEquals("events", record.topic());
        assertEquals("REQ-1", record.key());
        assertEquals("{\"a\":1}", record.value());
        assertEquals("evt-1", new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8));
        assertEquals("TRANSACTION_COMPLETED",
                new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8));
        verify(service).markPublished(7L, "p-1");
        verify(service, never()).recordFailure(any(), anyString(), anyString());
    }

    @Test
    void failedSendRecordsFailureAndDoesNotMarkPublished() {
        CompletableFuture failed = new CompletableFuture();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        assertFalse(publisher.publish(event, "p-1"));

        verify(service).recordFailure(eq(7L), eq("p-1"), contains("broker down"));
        verify(service, never()).markPublished(any(), any());
    }

    @Test
    void pollStopsAfterFirstFailureInsteadOfBurningAttempts() {
        CompletableFuture failed = new CompletableFuture();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);
        when(service.claimNext(anyString())).thenReturn(Optional.of(event));

        publisher.poll();

        verify(service, times(1)).claimNext(anyString());
        verify(service).recordFailure(eq(7L), anyString(), anyString());
    }

    @Test
    void pollDrainsUntilNothingIsDue() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn((CompletableFuture) CompletableFuture.completedFuture(null));
        when(service.markPublished(any(), anyString())).thenReturn(true);
        when(service.claimNext(anyString()))
                .thenReturn(Optional.of(event), Optional.of(event), Optional.empty());

        publisher.poll();

        verify(service, times(3)).claimNext(anyString());
        verify(service, times(2)).markPublished(eq(7L), anyString());
    }

    @Test
    void disabledPublisherDoesNothing() {
        ReflectionTestUtils.setField(publisher, "enabled", false);

        publisher.poll();

        verifyNoInteractions(service, kafkaTemplate);
    }
}

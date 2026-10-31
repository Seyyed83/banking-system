package com.example.banking.audit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TransactionEventConsumerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final TransactionEventConsumer consumer = new TransactionEventConsumer(jdbcTemplate);

    @Test
    void newEventIsRecorded() {
        assertTrue(consumer.handle("evt-1", "TRANSACTION_COMPLETED", "REQ-1", "{}"));

        verify(jdbcTemplate).update(anyString(), eq("evt-1"), eq("TRANSACTION_COMPLETED"), eq("REQ-1"), eq("{}"));
    }

    @Test
    void duplicateDeliveryIsIgnored() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("ORA-00001"));

        assertFalse(consumer.handle("evt-1", "TRANSACTION_COMPLETED", "REQ-1", "{}"));
    }

    @Test
    void malformedEventsAreSkippedWithoutTouchingTheDatabase() {
        assertFalse(consumer.handle(null, "TYPE", "REQ-1", "{}"));
        assertFalse(consumer.handle(" ", "TYPE", "REQ-1", "{}"));
        assertFalse(consumer.handle("evt-1", null, "REQ-1", "{}"));
        assertFalse(consumer.handle("evt-1", "TYPE", null, "{}"));
        assertFalse(consumer.handle("evt-1", "TYPE", "REQ-1", null));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void onMessageReadsHeadersFromTheRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("events", 0, 0L, "REQ-9", "{\"x\":1}");
        record.headers().add("eventId", "evt-9".getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", "TRANSACTION_FAILED".getBytes(StandardCharsets.UTF_8));

        consumer.onMessage(record);

        verify(jdbcTemplate).update(anyString(), eq("evt-9"), eq("TRANSACTION_FAILED"), eq("REQ-9"), eq("{\"x\":1}"));
    }

    @Test
    void onMessageWithoutHeadersIsSkipped() {
        consumer.onMessage(new ConsumerRecord<>("events", 0, 0L, "REQ-9", "{}"));

        verifyNoInteractions(jdbcTemplate);
    }
}

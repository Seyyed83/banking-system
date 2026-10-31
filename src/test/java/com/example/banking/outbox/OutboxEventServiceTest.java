package com.example.banking.outbox;

import com.example.banking.transaction.entity.FinancialTransaction;
import com.example.banking.transaction.entity.FinancialTransactionType;
import com.example.banking.transaction.entity.TransactionRequest;
import com.example.banking.transaction.entity.TransactionRequestStatus;
import com.example.banking.transaction.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventServiceTest {

    private OutboxEventRepository repository;
    private OutboxEventService service;

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(call -> call.getArgument(0));
        service = new OutboxEventService(repository);
    }

    private TransactionRequest request(TransactionRequestStatus status, String failureCode) {
        TransactionRequest request = new TransactionRequest(
                "REQ-1", "client-1", "key-1", TransactionType.TRANSFER,
                10L, 20L, new BigDecimal("1000.5000"));
        ReflectionTestUtils.setField(request, "status", status);
        ReflectionTestUtils.setField(request, "failureCode", failureCode);
        return request;
    }

    @Test
    void completedEventIsPendingAndCarriesTrackingCodes() {
        FinancialTransaction transfer = new FinancialTransaction(
                1L, null, "FTX-A", FinancialTransactionType.TRANSFER, new BigDecimal("1000.5000"));
        FinancialTransaction fee = new FinancialTransaction(
                1L, 5L, "FTX-B", FinancialTransactionType.FEE, new BigDecimal("500.0000"));

        service.recordCompleted(request(TransactionRequestStatus.COMPLETED, null), List.of(transfer, fee));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();

        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertEquals("TRANSACTION_REQUEST", event.getAggregateType());
        assertEquals("REQ-1", event.getAggregateId());
        assertEquals("TRANSACTION_COMPLETED", event.getEventType());
        assertTrue(event.getPayload().contains("\"eventId\":\"" + event.getEventId() + "\""));
        assertTrue(event.getPayload().contains("\"requestCode\":\"REQ-1\""));
        assertTrue(event.getPayload().contains("\"sourceAccountId\":10"));
        assertTrue(event.getPayload().contains("\"destinationAccountId\":20"));
        assertTrue(event.getPayload().contains("\"requestStatus\":\"COMPLETED\""));
        assertTrue(event.getPayload().contains("\"trackingCode\":\"FTX-A\",\"type\":\"TRANSFER\",\"amount\":\"1000.5000\""));
        assertTrue(event.getPayload().contains("\"trackingCode\":\"FTX-B\",\"type\":\"FEE\",\"amount\":\"500.0000\""));
    }

    @Test
    void failedEventCarriesFailureCode() {
        service.recordFailed(request(TransactionRequestStatus.FAILED, "INSUFFICIENT_FUNDS"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent event = captor.getValue();

        assertEquals("TRANSACTION_FAILED", event.getEventType());
        assertTrue(event.getPayload().contains("\"failureCode\":\"INSUFFICIENT_FUNDS\""));
        assertTrue(event.getPayload().contains("\"financialTransactions\":[]"));
    }

    @Test
    void depositHasNullSourceAccount() {
        TransactionRequest deposit = new TransactionRequest(
                "REQ-2", "client-1", "key-2", TransactionType.DEPOSIT,
                null, 20L, new BigDecimal("5.0000"));
        ReflectionTestUtils.setField(deposit, "status", TransactionRequestStatus.COMPLETED);

        service.recordCompleted(deposit, List.of());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"sourceAccountId\":null"));
    }

    @Test
    void quoteEscapesSpecialCharacters() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", OutboxPayloads.quote("a\"b\\c\nd"));
        assertEquals("null", OutboxPayloads.quote(null));
        assertEquals("\"\\u0001\"", OutboxPayloads.quote("\u0001"));
    }
}

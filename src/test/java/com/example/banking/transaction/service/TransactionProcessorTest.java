package com.example.banking.transaction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {

    @Mock
    private TransactionExecutionService executionService;

    @Mock
    private TransactionRequestService requestService;

    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor(
                executionService,
                requestService);
    }

    @Test
    void processRequestExecutesUsingProvidedWorkerId() {
        processor.processRequest(42L, "worker-1");

        verify(executionService).execute(42L, "worker-1");
        verifyNoInteractions(requestService);
    }

    @Test
    void financialOperationFailureMarksRequestFailed() {
        doThrow(new FinancialOperationException(
                "INSUFFICIENT_FUNDS",
                "Insufficient balance"))
                .when(executionService).execute(42L, "worker-1");

        assertDoesNotThrow(
                () -> processor.processRequest(42L, "worker-1"));

        verify(requestService).markFailed(
                42L,
                "worker-1",
                "INSUFFICIENT_FUNDS");

        verify(requestService, never()).recordTransientFailure(
                any(Long.class),
                anyString(),
                anyString());
    }

    @Test
    void unexpectedFailureSchedulesTransientRetry() {
        doThrow(new IllegalStateException("temporary failure"))
                .when(executionService).execute(42L, "worker-1");

        assertDoesNotThrow(
                () -> processor.processRequest(42L, "worker-1"));

        verify(requestService).recordTransientFailure(
                42L,
                "worker-1",
                "TRANSIENT_PROCESSING_ERROR");

        verify(requestService, never()).markFailed(
                any(Long.class),
                anyString(),
                anyString());
    }

    @Test
    void processingDoesNotThrowWhenRecordingRetryAlsoFails() {
        doThrow(new IllegalStateException("processing failure"))
                .when(executionService).execute(42L, "worker-1");

        doThrow(new IllegalStateException("database failure"))
                .when(requestService).recordTransientFailure(
                        42L,
                        "worker-1",
                        "TRANSIENT_PROCESSING_ERROR");

        assertDoesNotThrow(
                () -> processor.processRequest(42L, "worker-1"));

        verify(requestService).recordTransientFailure(
                42L,
                "worker-1",
                "TRANSIENT_PROCESSING_ERROR");
    }

    @Test
    void requestIsProcessedOnlyAfterSuccessfulClaim() {
        when(requestService.claimById(eq(42L), anyString()))
                .thenReturn(true);

        processor.processRequest(42L);

        ArgumentCaptor<String> workerIdCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(requestService).claimById(
                eq(42L),
                workerIdCaptor.capture());

        String workerId = workerIdCaptor.getValue();

        assertTrue(workerId.startsWith("integration-"));
        verify(executionService).execute(42L, workerId);
    }

    @Test
    void requestIsNotProcessedWhenClaimFails() {
        when(requestService.claimById(eq(42L), anyString()))
                .thenReturn(false);

        processor.processRequest(42L);

        verify(requestService).claimById(eq(42L), anyString());
        verifyNoInteractions(executionService);
    }
}
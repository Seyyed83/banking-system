package com.example.banking.transaction.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionWorkerTest {

    @Mock
    private TransactionRequestService requestService;

    @Mock
    private TransactionProcessor processor;

    private TransactionWorker worker;

    @BeforeEach
    void setUp() {
        worker = new TransactionWorker(requestService, processor);
    }

    @Test
    void pollClaimsAndProcessesAvailableRequest() {
        when(requestService.claimNext(anyString()))
                .thenReturn(Optional.of(42L));

        worker.poll();

        ArgumentCaptor<String> workerIdCaptor =
                ArgumentCaptor.forClass(String.class);

        InOrder order = inOrder(requestService, processor);

        order.verify(requestService).failExpiredExhaustedRequests();
        order.verify(requestService)
                .claimNext(workerIdCaptor.capture());

        String workerId = workerIdCaptor.getValue();

        assertNotNull(workerId);
        assertTrue(workerId.startsWith("worker-"));

        order.verify(processor).processRequest(42L, workerId);
    }

    @Test
    void pollDoesNotProcessAnythingWhenQueueIsEmpty() {
        when(requestService.claimNext(anyString()))
                .thenReturn(Optional.empty());

        worker.poll();

        verify(requestService).failExpiredExhaustedRequests();
        verify(requestService).claimNext(anyString());
        verifyNoInteractions(processor);
    }

    @Test
    void pollStopsWhenExpiredRequestCleanupFails() {
        doThrow(new IllegalStateException("cleanup failure"))
                .when(requestService).failExpiredExhaustedRequests();

        assertDoesNotThrow(() -> worker.poll());

        verify(requestService).failExpiredExhaustedRequests();
        verify(requestService, never()).claimNext(anyString());
        verifyNoInteractions(processor);
    }

    @Test
    void pollHandlesClaimFailureWithoutPropagatingException() {
        when(requestService.claimNext(anyString()))
                .thenThrow(new IllegalStateException("claim failure"));

        assertDoesNotThrow(() -> worker.poll());

        verify(requestService).failExpiredExhaustedRequests();
        verify(requestService).claimNext(anyString());
        verifyNoInteractions(processor);
    }
}
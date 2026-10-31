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
                .thenReturn(Optional.of(42L), Optional.empty());

        worker.poll();

        ArgumentCaptor<String> claimCaptor =
                ArgumentCaptor.forClass(String.class);

        InOrder order = inOrder(requestService, processor);

        order.verify(requestService).failExpiredExhaustedRequests();
        order.verify(requestService)
                .claimNext(claimCaptor.capture());

        String firstClaim = claimCaptor.getValue();

        assertNotNull(firstClaim);
        assertTrue(firstClaim.startsWith("worker-"));
        assertTrue(firstClaim.length() <= 100, "must fit WORKER_ID VARCHAR2(100)");

        order.verify(processor).processRequest(42L, firstClaim);

        ArgumentCaptor<String> secondCaptor =
                ArgumentCaptor.forClass(String.class);
        order.verify(requestService).claimNext(secondCaptor.capture());
        assertNotEquals(firstClaim, secondCaptor.getValue(),
                "every claim must use its own unique token");
    }

    @Test
    void pollDoesNothingWhenWorkerIsDisabled() {
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "enabled", false);

        worker.poll();

        verifyNoInteractions(requestService, processor);
    }

    @Test
    void pollProcessesBatchOfRequestsUntilQueueIsEmpty() {
        when(requestService.claimNext(anyString()))
                .thenReturn(Optional.of(10L), Optional.of(20L), Optional.empty());

        worker.poll();

        verify(requestService).failExpiredExhaustedRequests();
        verify(requestService, times(3)).claimNext(anyString());
        verify(processor).processRequest(eq(10L), anyString());
        verify(processor).processRequest(eq(20L), anyString());
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
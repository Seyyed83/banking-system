package com.example.banking.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableScheduling
public class TransactionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionWorker.class);

    private final TransactionRequestService requestService;
    private final TransactionProcessor processor;

    public TransactionWorker(
            TransactionRequestService requestService,
            TransactionProcessor processor) {
        this.requestService = requestService;
        this.processor = processor;
    }

    @Scheduled(
            fixedDelayString =
                    "${banking.worker.poll-interval-ms:500}")
    public void poll() {
        try {
            requestService.failExpiredExhaustedRequests();

            String workerId = "worker-" + UUID.randomUUID();

            Optional<Long> requestId =
                    requestService.claimNext(workerId);

            requestId.ifPresent(
                    id -> processor.processRequest(id, workerId));

        } catch (Exception exception) {
            log.error(
                    "Transaction worker polling failed.",
                    exception);
        }
    }
}
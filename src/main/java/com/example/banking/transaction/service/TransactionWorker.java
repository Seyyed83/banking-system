package com.example.banking.transaction.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableScheduling
public class TransactionWorker {

    private static final Logger log =
            LoggerFactory.getLogger(TransactionWorker.class);

    private final TransactionRequestService requestService;
    private final TransactionProcessor processor;
    /** Identifies this application instance in logs; each claim gets its own token (see poll). */
    private final String instanceId = "worker-" + UUID.randomUUID();

    @Value("${banking.worker.enabled:true}")
    private boolean enabled = true;

    @Value("${banking.worker.max-batch-size:50}")
    private int maxBatchSize = 50;

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
        if (!enabled) {
            return;
        }
        try {
            requestService.failExpiredExhaustedRequests();

            int limit = maxBatchSize > 0 ? maxBatchSize : 50;
            int processedCount = 0;

            for (int i = 0; i < limit; i++) {
                // One unique token per claim (<= 80 chars, WORKER_ID is VARCHAR2(100)).
                // The token is both the lookup key of the freshly claimed row and the fencing
                // token checked by execute()/markFailed(): a request that is stuck in PROCESSING
                // from an earlier claim can never be mistaken for the one claimed now, and a
                // stale execution can never act on a request that was re-claimed later.
                String workerId = instanceId + "-" + UUID.randomUUID();
                Optional<Long> requestId =
                        requestService.claimNext(workerId);

                if (requestId.isEmpty()) {
                    break;
                }

                Long id = requestId.get();
                log.info("Worker {} claimed transaction request id={} (claim={})", instanceId, id, workerId);
                processor.processRequest(id, workerId);
                processedCount++;
            }

            if (processedCount > 0) {
                log.info("Worker {} processed {} transaction request(s) in current poll cycle.",
                        instanceId, processedCount);
            }

        } catch (Exception exception) {
            log.error(
                    "Transaction worker polling failed for worker {}.",
                    instanceId,
                    exception);
        }
    }
}
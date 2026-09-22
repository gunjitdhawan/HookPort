package io.hookport.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DeliveryWorker {

    private final DeliveryStateService stateService;
    private final DeliveryProcessor processor;
    private final DeliveryProperties properties;
    private final DeliveryMetrics metrics;
    private static final Logger log =
            LoggerFactory.getLogger(DeliveryWorker.class);

    public DeliveryWorker(
            DeliveryStateService stateService,
            DeliveryProcessor processor,
            DeliveryProperties properties, DeliveryMetrics metrics
    ) {
        this.stateService = stateService;
        this.processor = processor;
        this.properties = properties;
        this.metrics = metrics;
    }

    public int runOnce() {
        int claimedCount = 0;

        while (claimedCount < properties.getBatchSize()) {
            ClaimDecision decision = stateService.claimNextDue();

            if (!decision.foundDueDelivery()) {
                break;
            }

            if (decision.claimed() == null) {
                continue; // Deferred one; look for another endpoint.
            }

            claimedCount++;
            metrics.recordClaimed(1);
            processor.processClaimed(decision.claimed());
        }

        return claimedCount;
    }

    public int recoverStuck() {
        Instant cutoff = Instant.now().minusSeconds(
                properties.getStuckTimeoutSeconds()
        );

        int recovered = stateService.recoverStuckDeliveries(
                cutoff,
                properties.getRecoveryBatchSize()
        );

        if (recovered > 0) {
            log.warn(
                    "Recovered stuck deliveries: count={}, cutoff={}",
                    recovered,
                    cutoff
            );
        }

        metrics.recordRecovered(recovered);

        return recovered;
    }
}
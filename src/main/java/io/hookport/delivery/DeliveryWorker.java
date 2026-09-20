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
        List<ClaimedDelivery> deliveries =
                stateService.claimDueBatch(
                        Instant.now(),
                        properties.getBatchSize()
                );

        metrics.recordClaimed(deliveries.size());

        if (!deliveries.isEmpty()) {
            log.info(
                    "Claimed delivery batch: count={}",
                    deliveries.size()
            );
        }

        for (ClaimedDelivery delivery : deliveries) {
            processor.processClaimed(delivery);
        }

        return deliveries.size();
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
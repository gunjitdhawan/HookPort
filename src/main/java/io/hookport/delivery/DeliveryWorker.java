package io.hookport.delivery;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DeliveryWorker {

    private final DeliveryStateService stateService;
    private final DeliveryProcessor processor;
    private final DeliveryProperties properties;

    public DeliveryWorker(
            DeliveryStateService stateService,
            DeliveryProcessor processor,
            DeliveryProperties properties
    ) {
        this.stateService = stateService;
        this.processor = processor;
        this.properties = properties;
    }

    public int runOnce() {
        List<ClaimedDelivery> deliveries =
                stateService.claimDueBatch(
                        Instant.now(),
                        properties.getBatchSize()
                );

        for (ClaimedDelivery delivery : deliveries) {
            processor.processClaimed(delivery);
        }

        return deliveries.size();
    }

    public int recoverStuck() {
        Instant cutoff = Instant.now().minusSeconds(
                properties.getStuckTimeoutSeconds()
        );

        return stateService.recoverStuckDeliveries(
                cutoff,
                properties.getRecoveryBatchSize()
        );
    }
}
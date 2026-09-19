package io.hookport.delivery;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeliveryProcessor {

    private final DeliveryStateService stateService;
    private final WebhookHttpSender httpSender;

    public DeliveryProcessor(
            DeliveryStateService stateService,
            WebhookHttpSender httpSender
    ) {
        this.stateService = stateService;
        this.httpSender = httpSender;
    }

    public DeliveryAttemptResponse process(UUID deliveryId) {
        ClaimedDelivery delivery =
                stateService.claim(deliveryId);

        boolean successful = httpSender.send(delivery);

        if (successful) {
            stateService.markDelivered(deliveryId);
        } else {
            stateService.markFailed(deliveryId);
        }

        return new DeliveryAttemptResponse(
                deliveryId,
                delivery.attemptNumber(),
                successful
                        ? DeliveryStatus.DELIVERED
                        : DeliveryStatus.RETRY_SCHEDULED
        );
    }
}
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

        WebhookSendResult result = httpSender.send(delivery);
        DeliveryStatus finalStatus = stateService.complete(
                deliveryId,
                delivery.attemptId(),
                result
        );


        return new DeliveryAttemptResponse(
                deliveryId,
                delivery.attemptNumber(),
                finalStatus
        );
    }
}
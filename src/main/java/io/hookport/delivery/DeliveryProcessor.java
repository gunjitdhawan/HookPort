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

        return processClaimed(delivery);
    }

    public DeliveryAttemptResponse processClaimed(
            ClaimedDelivery delivery
    ) {
        WebhookSendResult sendResult;

        try {
            sendResult = httpSender.send(delivery);
        } catch (RuntimeException exception) {
            sendResult = WebhookSendResult.networkFailure(
                    exception,
                    0
            );
        }

        DeliveryStatus finalStatus = stateService.complete(
                delivery.deliveryId(),
                delivery.attemptId(),
                sendResult
        );

        return new DeliveryAttemptResponse(
                delivery.deliveryId(),
                delivery.attemptNumber(),
                finalStatus
        );
    }
}
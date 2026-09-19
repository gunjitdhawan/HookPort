package io.hookport.delivery;

import java.util.UUID;

public record DeliveryAttemptResponse(
        UUID deliveryId,
        int attemptNumber,
        DeliveryStatus status
) {
}
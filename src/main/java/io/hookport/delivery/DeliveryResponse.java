package io.hookport.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryResponse(
        UUID id,
        UUID endpointId,
        UUID eventId,
        DeliveryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeliveryResponse from(
            WebhookDelivery delivery
    ) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getEndpoint().getId(),
                delivery.getEvent().getId(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getCreatedAt(),
                delivery.getUpdatedAt()
        );
    }
}
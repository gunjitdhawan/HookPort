package io.hookport.event;

import io.hookport.delivery.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record PublishEventResponse(
        UUID eventId,
        UUID deliveryId,
        String eventType,
        DeliveryStatus deliveryStatus,
        Instant acceptedAt,
        boolean replayed
) {
}
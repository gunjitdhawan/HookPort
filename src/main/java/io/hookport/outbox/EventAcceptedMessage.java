package io.hookport.outbox;

import java.util.UUID;

public record EventAcceptedMessage(
        UUID eventId,
        UUID endpointId,
        UUID deliveryId,
        String eventType
) {
}
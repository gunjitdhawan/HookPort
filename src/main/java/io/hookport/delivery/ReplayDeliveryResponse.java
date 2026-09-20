package io.hookport.delivery;

import java.util.UUID;

public record ReplayDeliveryResponse(
        UUID originalDeliveryId,
        UUID replayDeliveryId,
        DeliveryStatus status
) {
}
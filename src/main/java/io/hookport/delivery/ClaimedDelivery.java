package io.hookport.delivery;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ClaimedDelivery(
        UUID deliveryId,
        UUID attemptId,
        UUID eventId,
        String eventType,
        JsonNode payload,
        Instant eventCreatedAt,
        String targetUrl,
        int attemptNumber
) {
}
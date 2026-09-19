package io.hookport.delivery;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WebhookEnvelope(
        UUID id,
        String type,
        Instant createdAt,
        JsonNode data
) {
}
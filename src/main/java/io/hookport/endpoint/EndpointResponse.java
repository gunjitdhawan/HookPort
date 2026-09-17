package io.hookport.endpoint;

import java.time.Instant;
import java.util.UUID;

public record EndpointResponse(
    UUID id,
    String name,
    String targetUrl,
    EndpointStatus status,
    Instant createdAt,
    Instant updatedAt,
    Long version) {
    public static EndpointResponse from(WebhookEndpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getTargetUrl(),
                endpoint.getStatus(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt(),
                endpoint.getVersion()
        );
    }
}

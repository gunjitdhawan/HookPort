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
    Long version,
    int bucketCapacity,
    int refillPerSecond) {


    public static EndpointResponse from(WebhookEndpoint endpoint, EndpointRateBucket bucket) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getName(),
                endpoint.getTargetUrl(),
                endpoint.getStatus(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt(),
                endpoint.getVersion(),
                bucket.getCapacity(),
                bucket.getRefillPerSecond()
        );
    }
}

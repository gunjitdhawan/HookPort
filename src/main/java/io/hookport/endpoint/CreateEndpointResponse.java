package io.hookport.endpoint;

import java.time.Instant;
import java.util.UUID;

public record CreateEndpointResponse(
        UUID id,
        String name,
        String targetUrl,
        EndpointStatus status,
        String signingSecret,
        Instant createdAt
) {
}
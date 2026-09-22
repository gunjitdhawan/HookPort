package io.hookport.endpoint;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateEndpointRequest(
        @Size(min = 1, max = 100)
        String name,

        @Size(min = 1, max = 2048)
        String targetUrl,

        EndpointStatus status,

        @Positive
        Integer bucketCapacity,

        @Positive
        Integer refillPerSecond
) {
}

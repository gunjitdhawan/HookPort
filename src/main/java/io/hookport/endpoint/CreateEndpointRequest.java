package io.hookport.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEndpointRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 2048)
        String targetUrl,

        @Positive
        Integer bucketCapacity,

        @Positive
        Integer refillPerSecond
) {
}
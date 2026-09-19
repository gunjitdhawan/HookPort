package io.hookport.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record PublishEventRequest(

        @NotBlank
        @Size(max = 100)
        String eventType,

        @NotNull
        JsonNode payload

) {
}
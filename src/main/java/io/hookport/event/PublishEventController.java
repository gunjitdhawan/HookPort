package io.hookport.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/endpoints/{endpointId}/events")
public class PublishEventController {

    private static final String IDEMPOTENCY_KEY =
            "Idempotency-Key";

    private final PublishEventService service;

    public PublishEventController(PublishEventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PublishEventResponse> publish(
            @PathVariable UUID endpointId,
            @RequestHeader(
                    value = IDEMPOTENCY_KEY,
                    required = false
            ) String idempotencyKey,
            @Valid @RequestBody PublishEventRequest request
    ) {
        PublishEventResponse response = service.publish(
                endpointId,
                idempotencyKey,
                request
        );

        if (response.replayed()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header(
                        HttpHeaders.LOCATION,
                        "/api/v1/deliveries/" + response.deliveryId()
                )
                .body(response);
    }
}
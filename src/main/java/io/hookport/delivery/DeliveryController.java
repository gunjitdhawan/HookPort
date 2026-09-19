package io.hookport.delivery;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryProcessor processor;

    public DeliveryController(DeliveryProcessor processor) {
        this.processor = processor;
    }

    @PostMapping("/{deliveryId}/attempt")
    public ResponseEntity<DeliveryAttemptResponse> attempt(
            @PathVariable UUID deliveryId
    ) {
        return ResponseEntity.ok(
                processor.process(deliveryId)
        );
    }
}
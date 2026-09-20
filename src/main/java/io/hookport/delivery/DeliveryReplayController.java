package io.hookport.delivery;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryReplayController {

    private final DeliveryReplayService replayService;

    public DeliveryReplayController(
            DeliveryReplayService replayService
    ) {
        this.replayService = replayService;
    }

    @PostMapping("/{deliveryId}/replay")
    public ResponseEntity<ReplayDeliveryResponse> replay(
            @PathVariable UUID deliveryId
    ) {
        ReplayDeliveryResponse response =
                replayService.replay(deliveryId);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/deliveries/"
                                + response.replayDeliveryId()
                ))
                .body(response);
    }
}
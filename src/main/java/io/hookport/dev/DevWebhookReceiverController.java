package io.hookport.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@Profile("dev")
@RestController
@RequestMapping("/dev/webhook-receiver")
public class DevWebhookReceiverController {

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody JsonNode body
    ) {
        System.out.println("Received webhook: " + body);

        return ResponseEntity.noContent().build();
    }
}
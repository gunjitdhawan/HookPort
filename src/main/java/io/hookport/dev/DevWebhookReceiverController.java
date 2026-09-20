package io.hookport.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;

@Profile("dev")
@RestController
@RequestMapping("/dev/webhook-receiver")
public class DevWebhookReceiverController {

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader("X-HookPort-Event-Id")
            String eventId,

            @RequestHeader("X-HookPort-Timestamp")
            String timestamp,

            @RequestHeader("X-HookPort-Signature")
            String signature,

            @RequestBody byte[] body
    ) {

        System.out.println("Event ID: " + eventId);
        System.out.println("Timestamp: " + timestamp);
        System.out.println("Signature: " + signature);
        System.out.println(
                "Body: " +
                        new String(
                                body,
                                StandardCharsets.UTF_8
                        )
        );

        return ResponseEntity.noContent().build();
    }
}
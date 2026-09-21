package io.hookport.outbox;

import java.util.UUID;

public record PendingOutboxMessage(
        UUID eventId,
        String topic,
        String payload
) {
}
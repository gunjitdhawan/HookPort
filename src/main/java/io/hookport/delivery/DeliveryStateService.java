package io.hookport.delivery;

import io.hookport.endpoint.EndpointStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class DeliveryStateService {

    private final WebhookDeliveryRepository repository;

    public DeliveryStateService(
            WebhookDeliveryRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public ClaimedDelivery claim(UUID deliveryId) {
        WebhookDelivery delivery = find(deliveryId);

        if (delivery.getEndpoint().getStatus()
                != EndpointStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The webhook endpoint is disabled"
            );
        }

        try {
            delivery.claim();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        repository.flush();

        return new ClaimedDelivery(
                delivery.getId(),
                delivery.getEvent().getId(),
                delivery.getEvent().getEventType(),
                delivery.getEvent().getPayload(),
                delivery.getEvent().getCreatedAt(),
                delivery.getEndpoint().getTargetUrl(),
                delivery.getAttemptCount()
        );
    }

    @Transactional
    public void markDelivered(UUID deliveryId) {
        WebhookDelivery delivery = find(deliveryId);
        delivery.markDelivered();
        repository.flush();
    }

    @Transactional
    public void markFailed(UUID deliveryId) {
        WebhookDelivery delivery = find(deliveryId);

        // Temporary fixed delay; exponential backoff comes later.
        Instant retryAt = Instant.now()
                .plus(30, ChronoUnit.SECONDS);

        delivery.markRetryScheduled(retryAt);
        repository.flush();
    }

    private WebhookDelivery find(UUID deliveryId) {
        return repository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Webhook delivery not found"
                ));
    }
}
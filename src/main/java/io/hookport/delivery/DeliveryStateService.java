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
    private final DeliveryAttemptRepository attemptRepository;


    public DeliveryStateService(
            WebhookDeliveryRepository repository,
            DeliveryAttemptRepository attemptRepository
    ) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
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

        DeliveryAttempt attempt = attemptRepository.save(DeliveryAttempt.start(delivery, delivery.getAttemptCount()));

        repository.flush();
        attemptRepository.flush();

        return new ClaimedDelivery(
                delivery.getId(),
                delivery.getEvent().getId(),
                attempt.getId(),
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

    @Transactional
    public DeliveryStatus complete(
            UUID deliveryId,
            UUID attemptId,
            WebhookSendResult result
    ) {
        WebhookDelivery delivery = find(deliveryId);

        DeliveryAttempt attempt = attemptRepository
                .findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Delivery attempt not found"
                ));

        if (!attempt.getDelivery().getId().equals(deliveryId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Attempt does not belong to the delivery"
            );
        }

        attempt.complete(result);

        switch (result.outcome()) {
            case DELIVERED ->
                    delivery.markDelivered();

            case RETRYABLE_FAILURE ->
                    delivery.markRetryScheduled(
                            Instant.now().plusSeconds(30)
                    );

            case PERMANENT_FAILURE ->
                    delivery.markFailedPermanently();
        }

        repository.flush();
        attemptRepository.flush();

        return delivery.getStatus();
    }
}
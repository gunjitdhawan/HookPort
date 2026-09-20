package io.hookport.delivery;

import io.hookport.endpoint.EndpointStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static io.hookport.delivery.AttemptOutcome.PERMANENT_FAILURE;

@Service
public class DeliveryStateService {

    private final WebhookDeliveryRepository repository;
    private final DeliveryAttemptRepository attemptRepository;
    private final RetryPolicy retryPolicy;
    private final DeliveryProperties properties;


    public DeliveryStateService(
            WebhookDeliveryRepository repository,
            DeliveryAttemptRepository attemptRepository,
            RetryPolicy retryPolicy,
            DeliveryProperties properties
    ) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
    }

    @Transactional
    public ClaimedDelivery claim(UUID deliveryId) {
        WebhookDelivery delivery = find(deliveryId);
        return claimLoadedDelivery(delivery);
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

            case RETRYABLE_FAILURE -> {
                if (delivery.getAttemptCount()
                        >= properties.getMaxAttempts()) {
                    delivery.markExhausted();
                } else {
                    delivery.markRetryScheduled(
                            retryPolicy.nextRetryAt(
                                    Instant.now(),
                                    delivery.getAttemptCount()
                            )
                    );
                }
            }

            case PERMANENT_FAILURE ->
                    delivery.markFailedPermanently();
        }

        repository.flush();
        attemptRepository.flush();

        return delivery.getStatus();
    }

    @Transactional
    public List<ClaimedDelivery> claimDueBatch(
            Instant now,
            int batchSize
    ) {
        List<WebhookDelivery> deliveries =
                repository.findDueForUpdate(now, batchSize);

        List<ClaimedDelivery> claimed = deliveries.stream()
                .map(this::claimLoadedDelivery)
                .toList();

        repository.flush();
        attemptRepository.flush();

        return claimed;
    }

    private ClaimedDelivery claimLoadedDelivery(
            WebhookDelivery delivery
    ) {
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

        DeliveryAttempt attempt = attemptRepository.save(
                DeliveryAttempt.start(
                        delivery,
                        delivery.getAttemptCount()
                )
        );

        return new ClaimedDelivery(
                delivery.getId(),
                attempt.getId(),
                delivery.getEvent().getId(),
                delivery.getEvent().getEventType(),
                delivery.getEvent().getPayload(),
                delivery.getEvent().getCreatedAt(),
                delivery.getEndpoint().getTargetUrl(),
                delivery.getEndpoint().getSigningSecret(),
                delivery.getAttemptCount()
        );
    }

    @Transactional
    public int recoverStuckDeliveries(
            Instant cutoff,
            int batchSize
    ) {
        List<WebhookDelivery> stuckDeliveries =
                repository.findStuckForUpdate(
                        cutoff,
                        batchSize
                );

        Instant now = Instant.now();

        for (WebhookDelivery delivery : stuckDeliveries) {
            DeliveryAttempt attempt = attemptRepository
                    .findFirstByDeliveryIdAndCompletedAtIsNullOrderByAttemptNumberDesc(
                            delivery.getId()
                    )
                    .orElseThrow(() -> new IllegalStateException(
                            "IN_PROGRESS delivery has no incomplete attempt: "
                                    + delivery.getId()
                    ));

            long durationMs = Math.max(
                    0,
                    Duration.between(
                            attempt.getStartedAt(),
                            now
                    ).toMillis()
            );

            attempt.complete(
                    WebhookSendResult.abandoned(durationMs)
            );

            if (delivery.getAttemptCount()
                    >= properties.getMaxAttempts()) {
                delivery.markExhausted();
            } else {
                delivery.markRetryScheduled(
                        retryPolicy.nextRetryAt(
                                now,
                                delivery.getAttemptCount()
                        )
                );
            }
        }

        attemptRepository.flush();
        repository.flush();

        return stuckDeliveries.size();
    }
}
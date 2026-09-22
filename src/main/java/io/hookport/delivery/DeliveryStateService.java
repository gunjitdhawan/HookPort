package io.hookport.delivery;

import io.hookport.endpoint.EndpointRateBucket;
import io.hookport.endpoint.EndpointRateBucketRepository;
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
    private final EndpointRateBucketRepository buckets;

    public DeliveryStateService(
            WebhookDeliveryRepository repository,
            DeliveryAttemptRepository attemptRepository,
            RetryPolicy retryPolicy,
            DeliveryProperties properties,
            EndpointRateBucketRepository endpointRateBucketRepository
    ) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.buckets = endpointRateBucketRepository;
    }

    @Transactional
    public ClaimedDelivery claim(UUID deliveryId) {
        WebhookDelivery delivery = repository
                .findByIdForClaim(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Delivery is locked or does not exist"
                ));

        if (delivery.getEndpoint().getStatus()
                != EndpointStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The webhook endpoint is disabled"
            );
        }

        if (delivery.getStatus() != DeliveryStatus.PENDING
                && delivery.getStatus()
                != DeliveryStatus.RETRY_SCHEDULED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Delivery is not claimable"
            );
        }

        if (delivery.getNextAttemptAt() != null
                && delivery.getNextAttemptAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Delivery is not due yet"
            );
        }

        EndpointRateBucket bucket = buckets
                .findForClaim(delivery.getEndpoint().getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Endpoint bucket is busy"
                ));

        if (!bucket.trySpendOne(Instant.now())) {
            // Do not defer here: throwing rolls this transaction back.
            // The scheduler can defer the delivery on its next pass.
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Endpoint rate limit is exhausted"
            );
        }

        ClaimedDelivery claimed = claimLoadedDelivery(delivery);
        buckets.flush();
        repository.flush();
        attemptRepository.flush();
        return claimed;
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
    public ClaimDecision claimNextDue() {
        Instant now = Instant.now();

        List<WebhookDelivery> due =
                repository.findDueForUpdate(now, 1);

        if (due.isEmpty()) {
            return ClaimDecision.empty();
        }

        WebhookDelivery delivery = due.getFirst();

        var lockedBucket = buckets.findForClaim(
                delivery.getEndpoint().getId()
        );

        if (lockedBucket.isEmpty()) {
            // Another worker has the bucket. Move this delivery briefly
            // so this polling pass can reach other endpoints.
            delivery.deferUntil(now.plusMillis(100));
            repository.flush();
            return ClaimDecision.deferred();
        }

        EndpointRateBucket bucket = lockedBucket.get();

        if (!bucket.trySpendOne(now)) {
            delivery.deferUntil(bucket.nextTokenAt(now));
            buckets.flush();
            repository.flush();
            return ClaimDecision.deferred();
        }

        ClaimedDelivery claimed = claimLoadedDelivery(delivery);
        buckets.flush();
        repository.flush();
        attemptRepository.flush();

        return ClaimDecision.claimed(claimed);
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
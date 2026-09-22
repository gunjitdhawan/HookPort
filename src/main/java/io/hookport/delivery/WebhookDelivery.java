package io.hookport.delivery;

import io.hookport.endpoint.WebhookEndpoint;
import io.hookport.event.WebhookEvent;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private WebhookEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private WebhookEndpoint endpoint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replayed_from_delivery_id")
    private WebhookDelivery replayedFrom;

    @Version
    @Column(nullable = false)
    private Long version;

    protected WebhookDelivery() {
    }

    private WebhookDelivery(
            WebhookEvent event,
            WebhookEndpoint endpoint
    ) {
        Instant now = Instant.now();

        this.event = event;
        this.endpoint = endpoint;
        this.status = DeliveryStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;

        // version deliberately remains null
    }

    public static WebhookDelivery pending(
            WebhookEvent event,
            WebhookEndpoint endpoint
    ) {
        return new WebhookDelivery(event, endpoint);
    }

    public UUID getId() {
        return id;
    }

    public WebhookEvent getEvent() {
        return event;
    }

    public WebhookEndpoint getEndpoint() {
        return endpoint;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void claim() {
        if (status != DeliveryStatus.PENDING &&
                status != DeliveryStatus.RETRY_SCHEDULED) {
            throw new IllegalStateException(
                    "Delivery cannot be claimed from status " + status
            );
        }

        status = DeliveryStatus.IN_PROGRESS;
        attemptCount++;
        nextAttemptAt = null;
        updatedAt = Instant.now();
    }

    public void markDelivered() {
        requireInProgress();

        status = DeliveryStatus.DELIVERED;
        nextAttemptAt = null;
        updatedAt = Instant.now();
    }

    public void markRetryScheduled(Instant retryAt) {
        requireInProgress();

        status = DeliveryStatus.RETRY_SCHEDULED;
        nextAttemptAt = retryAt;
        updatedAt = Instant.now();
    }

    public void markFailedPermanently() {
        requireInProgress();

        status = DeliveryStatus.FAILED;
        nextAttemptAt = null;
        updatedAt = Instant.now();
    }

    public void markExhausted() {
        requireInProgress();

        status = DeliveryStatus.EXHAUSTED;
        nextAttemptAt = null;
        updatedAt = Instant.now();
    }

    private void requireInProgress() {
        if (status != DeliveryStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Delivery must be IN_PROGRESS, but was " + status
            );
        }
    }

    public static WebhookDelivery replayOf(
            WebhookDelivery original
    ) {
        WebhookDelivery replay = new WebhookDelivery();

        replay.endpoint = original.endpoint;
        replay.event = original.event;
        replay.status = DeliveryStatus.PENDING;
        replay.attemptCount = 0;
        replay.nextAttemptAt = null;
        replay.replayedFrom = original;

        Instant now = Instant.now();
        replay.createdAt = now;
        replay.updatedAt = now;

        return replay;
    }

    public WebhookDelivery getReplayedFrom() {
        return replayedFrom;
    }

    public boolean canReplay() {
        return status == DeliveryStatus.FAILED
                || status == DeliveryStatus.EXHAUSTED;
    }

    public void deferUntil(Instant eligibleAt) {
        if (status != DeliveryStatus.PENDING
                && status != DeliveryStatus.RETRY_SCHEDULED) {
            throw new IllegalStateException(
                    "Only a waiting delivery can be deferred"
            );
        }

        nextAttemptAt = eligibleAt;
        updatedAt = Instant.now();
    }
}
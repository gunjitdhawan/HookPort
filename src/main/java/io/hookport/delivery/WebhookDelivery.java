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
}
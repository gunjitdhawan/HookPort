package io.hookport.delivery;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false)
    private WebhookDelivery delivery;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AttemptOutcome outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected DeliveryAttempt() {
    }

    private DeliveryAttempt(
            WebhookDelivery delivery,
            int attemptNumber
    ) {
        this.delivery = delivery;
        this.attemptNumber = attemptNumber;
        this.startedAt = Instant.now();
    }

    public static DeliveryAttempt start(
            WebhookDelivery delivery,
            int attemptNumber
    ) {
        return new DeliveryAttempt(delivery, attemptNumber);
    }

    public void complete(WebhookSendResult result) {
        if (completedAt != null) {
            throw new IllegalStateException(
                    "Delivery attempt is already complete"
            );
        }

        completedAt = Instant.now();
        outcome = result.outcome();
        httpStatus = result.httpStatus();
        errorMessage = truncate(result.errorMessage(), 1000);
        durationMs = result.durationMs();
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }

        return value.substring(0, maximumLength);
    }

    public UUID getId() {
        return id;
    }

    public WebhookDelivery getDelivery() {
        return delivery;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public AttemptOutcome getOutcome() {
        return outcome;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
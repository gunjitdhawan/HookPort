package io.hookport.endpoint;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EndpointStatus status;

    @Column(name = "signing_secret", nullable = false, length = 255)
    private String signingSecret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpoint() {
        // Required by JPA
    }

    private WebhookEndpoint(
            UUID id,
            String name,
            String targetUrl,
            EndpointStatus status,
            String signingSecret,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.targetUrl = targetUrl;
        this.status = status;
        this.signingSecret = signingSecret;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static WebhookEndpoint create(
            String name,
            String targetUrl,
            String signingSecret
    ) {
        Instant now = Instant.now();

        return new WebhookEndpoint(
                UUID.randomUUID(),
                name,
                targetUrl,
                EndpointStatus.ACTIVE,
                signingSecret,
                now,
                now
        );
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
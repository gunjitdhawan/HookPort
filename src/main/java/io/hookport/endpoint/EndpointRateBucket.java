package io.hookport.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "endpoint_rate_buckets")
public class EndpointRateBucket {

    @Id
    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "refill_per_second", nullable = false)
    private int refillPerSecond;

    @Column(nullable = false)
    private double tokens;

    @Column(name = "refilled_at", nullable = false)
    private Instant refilledAt;

    protected EndpointRateBucket() {
    }

    private EndpointRateBucket(
            UUID endpointId, int capacity,
            int refillPerSecond, Instant now
    ) {
        this.endpointId = endpointId;
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.refilledAt = now;
    }

    public static EndpointRateBucket full(
            UUID endpointId, int capacity,
            int refillPerSecond, Instant now
    ) {
        return new EndpointRateBucket(
                endpointId, capacity, refillPerSecond, now
        );
    }

    public boolean trySpendOne(Instant now) {
        refill(now);

        if (tokens < 1) {
            return false;
        }

        tokens -= 1;
        return true;
    }

    public Instant nextTokenAt(Instant now) {
        if (tokens >= 1) {
            return now;
        }

        double seconds = (1 - tokens) / refillPerSecond;
        long nanos = (long) Math.ceil(seconds * 1_000_000_000);
        return now.plusNanos(nanos);
    }

    public void changeSettings(
            int newCapacity, int newRefillPerSecond, Instant now
    ) {
        refill(now);
        capacity = newCapacity;
        refillPerSecond = newRefillPerSecond;
        tokens = Math.min(tokens, newCapacity);
    }

    private void refill(Instant now) {
        double elapsedSeconds = Math.max(
                0,
                Duration.between(refilledAt, now).toNanos()
                        / 1_000_000_000.0
        );

        tokens = Math.min(
                capacity,
                tokens + elapsedSeconds * refillPerSecond
        );
        refilledAt = now;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getRefillPerSecond() {
        return refillPerSecond;
    }
}
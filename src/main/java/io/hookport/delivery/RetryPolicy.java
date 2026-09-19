package io.hookport.delivery;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RetryPolicy {

    private final DeliveryProperties properties;

    public RetryPolicy(DeliveryProperties properties) {
        this.properties = properties;
    }

    public Instant nextRetryAt(
            Instant now,
            int completedAttemptCount
    ) {
        int exponent = Math.max(
                0,
                completedAttemptCount - 1
        );

        long delay = properties.getBaseDelaySeconds();

        for (int index = 0; index < exponent; index++) {
            if (delay >= properties.getMaxDelaySeconds() / 2) {
                delay = properties.getMaxDelaySeconds();
                break;
            }

            delay *= 2;
        }

        delay = Math.min(
                delay,
                properties.getMaxDelaySeconds()
        );

        double jitterMultiplier = ThreadLocalRandom
                .current()
                .nextDouble(0.8, 1.2);

        long delayMillis = (long) (
                delay * 1000 * jitterMultiplier
        );

        return now.plusMillis(delayMillis);
    }
}
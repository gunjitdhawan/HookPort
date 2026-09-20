package io.hookport.delivery;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class DeliveryMetrics {

    private final MeterRegistry registry;

    public DeliveryMetrics(
            MeterRegistry registry,
            WebhookDeliveryRepository deliveryRepository
    ) {
        this.registry = registry;

        Gauge.builder(
                        "hookport.delivery.queue.depth",
                        deliveryRepository,
                        repository -> repository.countByStatusIn(
                                List.of(
                                        DeliveryStatus.PENDING,
                                        DeliveryStatus.RETRY_SCHEDULED
                                )
                        )
                )
                .description(
                        "Number of deliveries waiting to be processed"
                )
                .register(registry);
    }

    public void recordAttempt(
            WebhookSendResult result,
            DeliveryStatus finalStatus
    ) {
        Counter.builder("hookport.delivery.attempts")
                .description(
                        "Number of webhook delivery attempts"
                )
                .tag(
                        "outcome",
                        result.outcome().name()
                )
                .tag(
                        "final_status",
                        finalStatus.name()
                )
                .register(registry)
                .increment();

        Timer.builder("hookport.delivery.http.duration")
                .description(
                        "Webhook HTTP request duration"
                )
                .tag(
                        "outcome",
                        result.outcome().name()
                )
                .register(registry)
                .record(Duration.ofMillis(
                        Math.max(0, result.durationMs())
                ));
    }

    public void recordClaimed(int count) {
        Counter.builder("hookport.delivery.claimed")
                .description(
                        "Number of deliveries claimed by workers"
                )
                .register(registry)
                .increment(count);
    }

    public void recordRecovered(int count) {
        Counter.builder("hookport.delivery.recovered")
                .description(
                        "Number of stuck deliveries recovered"
                )
                .register(registry)
                .increment(count);
    }
}
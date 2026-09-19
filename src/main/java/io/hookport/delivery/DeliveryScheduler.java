package io.hookport.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "hookport.delivery",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DeliveryScheduler {

    private final DeliveryWorker worker;

    public DeliveryScheduler(DeliveryWorker worker) {
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString =
                    "${hookport.delivery.poll-interval-ms:1000}"
    )
    public void poll() {
        worker.recoverStuck();
        worker.runOnce();
    }
}
package io.hookport.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log =
            LoggerFactory.getLogger(DeliveryScheduler.class);

    public DeliveryScheduler(DeliveryWorker worker) {
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString =
                    "${hookport.delivery.poll-interval-ms:1000}"
    )
    public void poll() {
        try {
            worker.recoverStuck();
        } catch (RuntimeException exception) {
            log.error(
                    "Stuck-delivery recovery failed",
                    exception
            );
        }

        try {
            worker.runOnce();
        } catch (RuntimeException exception) {
            log.error(
                    "Delivery polling failed",
                    exception
            );
        }
    }
}
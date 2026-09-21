package io.hookport.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "hookport.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EventOutboxScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(EventOutboxScheduler.class);

    private final EventOutboxRelay relay;

    public EventOutboxScheduler(EventOutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(
            fixedDelayString =
                    "${hookport.outbox.poll-interval-ms:1000}"
    )
    public void poll() {
        try {
            for (int i = 0; i < 20; i++) {
                if (!relay.publishOne()) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Outbox publication failed", exception);
        }
    }
}
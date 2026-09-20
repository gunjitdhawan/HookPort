package io.hookport.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeliveryProcessor {

    private final DeliveryStateService stateService;
    private final WebhookHttpSender httpSender;
    private final DeliveryMetrics metrics;
    private static final Logger log =
            LoggerFactory.getLogger(DeliveryProcessor.class);


    public DeliveryProcessor(
            DeliveryStateService stateService,
            WebhookHttpSender httpSender,
            DeliveryMetrics metrics
    ) {
        this.stateService = stateService;
        this.httpSender = httpSender;
        this.metrics = metrics;
    }

    public DeliveryAttemptResponse process(UUID deliveryId) {
        ClaimedDelivery delivery =
                stateService.claim(deliveryId);

        return processClaimed(delivery);
    }

    public DeliveryAttemptResponse processClaimed(
            ClaimedDelivery delivery
    ) {
        boolean generatedCorrelationId = false;
        if (MDC.get("correlationId") == null) {
            MDC.put(
                    "correlationId",
                    UUID.randomUUID().toString()
            );
            generatedCorrelationId = true;
        }

        try (
                MDC.MDCCloseable deliveryContext =
                        MDC.putCloseable(
                                "deliveryId",
                                delivery.deliveryId().toString()
                        );
                MDC.MDCCloseable eventContext =
                        MDC.putCloseable(
                                "eventId",
                                delivery.eventId().toString()
                        );
                MDC.MDCCloseable attemptContext =
                        MDC.putCloseable(
                                "attemptId",
                                delivery.attemptId().toString()
                        );
                MDC.MDCCloseable attemptNumberContext =
                        MDC.putCloseable(
                                "attemptNumber",
                                Integer.toString(
                                        delivery.attemptNumber()
                                )
                        )
        ) {
            log.info("Starting webhook delivery");
            WebhookSendResult sendResult;

            try {
                sendResult = httpSender.send(delivery);
            } catch (RuntimeException exception) {

                log.error(
                        "Unexpected webhook sender failure",
                        exception
                );

                sendResult = WebhookSendResult.networkFailure(
                        exception,
                        0
                );
            }

            DeliveryStatus finalStatus = stateService.complete(
                    delivery.deliveryId(),
                    delivery.attemptId(),
                    sendResult
            );

            metrics.recordAttempt(sendResult, finalStatus);
            if (finalStatus == DeliveryStatus.DELIVERED) {
                log.info(
                        "Webhook delivery completed: outcome={}, status={}, durationMs={}",
                        sendResult.outcome(),
                        finalStatus,
                        sendResult.durationMs()
                );
            } else {
                log.warn(
                        "Webhook delivery unsuccessful: outcome={}, status={}, httpStatus={}, durationMs={}",
                        sendResult.outcome(),
                        finalStatus,
                        sendResult.httpStatus(),
                        sendResult.durationMs()
                );
            }
            return new DeliveryAttemptResponse(
                    delivery.deliveryId(),
                    delivery.attemptNumber(),
                    finalStatus
            );
        } catch (RuntimeException exception) {
            /*
             * If completion fails, recovery can later find the
             * delivery left in IN_PROGRESS.
             */
            log.error(
                    "Delivery processing did not complete",
                    exception
            );

            throw exception;

        } finally {
            if (generatedCorrelationId) {
                MDC.remove("correlationId");
            }
        }
    }
}
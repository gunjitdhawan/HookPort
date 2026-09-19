package io.hookport.delivery;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WebhookHttpSender {

    private final RestClient restClient;

    public WebhookHttpSender(RestClient restClient) {
        this.restClient = restClient;
    }

    public WebhookSendResult send(ClaimedDelivery delivery) {
        WebhookEnvelope envelope = new WebhookEnvelope(
                delivery.eventId(),
                delivery.eventType(),
                delivery.eventCreatedAt(),
                delivery.payload()
        );

        long startedAt = System.nanoTime();

        try {
            int status = restClient
                    .post()
                    .uri(delivery.targetUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(
                            "X-HookPort-Event-Id",
                            delivery.eventId().toString()
                    )
                    .header(
                            "X-HookPort-Event-Type",
                            delivery.eventType()
                    )
                    .body(envelope)
                    .exchange((request, response) ->
                            response.getStatusCode().value()
                    );
            return WebhookSendResult.fromHttpStatus(
                    status,
                    elapsedMillis(startedAt)
            );

        } catch (RestClientException exception) {
            return WebhookSendResult.networkFailure(
                    exception,
                    elapsedMillis(startedAt)
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
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

    public boolean send(ClaimedDelivery delivery) {
        WebhookEnvelope envelope = new WebhookEnvelope(
                delivery.eventId(),
                delivery.eventType(),
                delivery.eventCreatedAt(),
                delivery.payload()
        );

        try {
            return restClient
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
                            response.getStatusCode()
                                    .is2xxSuccessful()
                    );
        } catch (RestClientException exception) {
            return false;
        }
    }
}
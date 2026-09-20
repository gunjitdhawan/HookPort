package io.hookport.delivery;

import io.hookport.security.TargetUrlValidator;
import io.hookport.security.UnsafeTargetUrlException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Component
public class WebhookHttpSender {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WebhookSigner signer;
    private final TargetUrlValidator targetUrlValidator;

    public WebhookHttpSender(RestClient restClient,
                             ObjectMapper objectMapper,
                             WebhookSigner signer, TargetUrlValidator targetUrlValidator) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.targetUrlValidator = targetUrlValidator;
    }

    public WebhookSendResult send(ClaimedDelivery delivery) {
        WebhookEnvelope envelope = new WebhookEnvelope(
                delivery.eventId(),
                delivery.eventType(),
                delivery.eventCreatedAt(),
                delivery.payload()
        );

        long startedAt = System.nanoTime();

        final byte[] body;

        try {
            body = objectMapper.writeValueAsBytes(envelope);
        } catch (Exception exception) {
            return WebhookSendResult.permanentFailure(
                    "Unable to serialize webhook payload",
                    elapsedMillis(startedAt)
            );
        }

        long timestamp = Instant.now().getEpochSecond();

        final String signature;

        try {
            signature = signer.sign(
                    delivery.signingSecret(),
                    timestamp,
                    body
            );
        } catch (IllegalStateException exception) {
            return WebhookSendResult.permanentFailure(
                    exception.getMessage(),
                    elapsedMillis(startedAt)
            );
        }

        try {
            targetUrlValidator.validate(delivery.targetUrl());
        } catch (UnsafeTargetUrlException exception) {
            return WebhookSendResult.permanentFailure(
                    exception.getMessage(),
                    elapsedMillis(startedAt)
            );
        }

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
                    .header(
                            "X-HookPort-Timestamp",
                            Long.toString(timestamp)
                    )
                    .header(
                            "X-HookPort-Signature",
                            signature
                    )
                    .body(body)
                    .exchange((request, response) ->
                            response.getStatusCode().value()
                    );

            if (status >= 300 && status < 400) {
                return WebhookSendResult.permanentFailure(
                        "Webhook redirects are not allowed",
                        elapsedMillis(startedAt)
                );
            }

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